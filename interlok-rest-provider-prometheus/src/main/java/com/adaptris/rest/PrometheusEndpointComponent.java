package com.adaptris.rest;

import static com.adaptris.rest.WorkflowServicesConsumer.ERROR_DEFAULT;
import java.util.function.Consumer;
import org.apache.commons.lang3.BooleanUtils;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.rest.metrics.MetricProviders;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PrometheusEndpointComponent  extends AbstractRestfulEndpoint {

  private static final String ACCEPTED_FILTER = "GET";

  private static final String DEFAULT_PATH = "/prometheus/metrics/*";

  private static final transient boolean ADDITIONAL_DEBUG =
      BooleanUtils.toBoolean(System.getProperty("interlok.prometheus.debug", "false"));

  @Getter(AccessLevel.PROTECTED)
  private transient final String defaultUrlPath = DEFAULT_PATH;

  @Getter(AccessLevel.PROTECTED)
  private transient final String acceptedFilter = ACCEPTED_FILTER;

  private transient PrometheusMeterRegistry prometheusRegistry = null;

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message, Consumer<AdaptrisMessage> success, Consumer<AdaptrisMessage> failure) {
    try {
      bindProviders();
      message.setContent(prometheusRegistry.scrape(), message.getContentEncoding());

      getConsumer().doResponse(message, message);
      success.accept(message);
    } catch (Exception ex) {
      getConsumer().doErrorResponse(message, ex, ERROR_DEFAULT);
      failure.accept(message);
    }
  }

  // sad, this is for coverage.
  protected void exceptionLogging(boolean logging, String msg, Exception e) {
    if (logging) {
      log.trace(msg, e);
    }
  }


  private void bindProviders() {
    MetricProviders.getProviders().forEach(provider -> {
      try {
        provider.bindTo(prometheusRegistry);
      } catch (Exception e) {
        // This is wrong, because if we've failed to bind, then we need to try and bind again.
        log.warn("Metric gathering failed, will try again on next request.");
        exceptionLogging(ADDITIONAL_DEBUG, "Stack trace from metric gathering failure :", e);
      }
    });
  }


  @Override
  public void start() throws Exception {
    prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    prometheusRegistry.config().meterFilter(new PrometheusRenameFilter());
    super.start();
  }

  @Override
  public void stop() throws Exception {
    prometheusRegistry.clear();
    super.stop();
  }


  @Override
  public void destroy() throws Exception {
    prometheusRegistry.close();
    super.destroy();
  }
}
