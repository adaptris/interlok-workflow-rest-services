package com.adaptris.rest.metrics.prometheus;

import static com.adaptris.rest.WorkflowServicesConsumer.ERROR_DEFAULT;

import java.util.Properties;
import java.util.function.Consumer;

import org.apache.commons.lang3.BooleanUtils;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.rest.AbstractRestfulEndpoint;
import com.adaptris.rest.metrics.MetricProviders;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusRenameFilter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PrometheusEndpointComponent extends AbstractRestfulEndpoint {

  private static final String ACCEPTED_FILTER = "GET";

  private static final String DEFAULT_PATH = "/prometheus/metrics/*";

  private static final String CONTENT_TYPE_DEFAULT = "text/plain";

  private static final transient boolean ADDITIONAL_DEBUG = BooleanUtils
      .toBoolean(System.getProperty("interlok.prometheus.debug", "false"));

  @Getter(AccessLevel.PROTECTED)
  private transient final String defaultUrlPath = DEFAULT_PATH;

  @Getter(AccessLevel.PROTECTED)
  private transient final String acceptedFilter = ACCEPTED_FILTER;

  private PrometheusMeterRegistry prometheusRegistry;

  @Override
  public void init(Properties config) throws Exception {
    prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    prometheusRegistry.config().meterFilter(new PrometheusRenameFilter());
  }

  @Override
  public void stop() throws Exception {
    prometheusRegistry.close();
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message, Consumer<AdaptrisMessage> success, Consumer<AdaptrisMessage> failure) {
    try {
      MetricProviders.getProviders().forEach(provider -> {
        try {
          provider.bindTo(prometheusRegistry);
        } catch (Exception e) {
          // This is wrong, because if we've failed to bind, then we need to try and bind again.
          log.warn("Metric gathering failed, will try again on next request.");
          exceptionLogging(ADDITIONAL_DEBUG, "Stack trace from metric gathering failure :", e);
        }
      });
      message.setContent(prometheusRegistry.scrape(), message.getContentEncoding());

      doResponse(message, message, CONTENT_TYPE_DEFAULT);
      success.accept(message);
    } catch (Exception ex) {
      doErrorResponse(message, ex, ERROR_DEFAULT);
      failure.accept(message);
    }
  }

  // sad, this is for coverage.
  protected void exceptionLogging(boolean logging, String msg, Exception e) {
    if (logging) {
      log.trace(msg, e);
    }
  }

}
