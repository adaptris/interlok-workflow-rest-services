package com.adaptris.rest.cluster;
import static com.adaptris.rest.WorkflowServicesConsumer.ERROR_DEFAULT;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import org.slf4j.MDC;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.CoreException;
import com.adaptris.core.XStreamJsonMarshaller;
import com.adaptris.mgmt.cluster.ClusterInstance;
import com.adaptris.mgmt.cluster.mbean.ClusterManagerMBean;
import com.adaptris.rest.AbstractRestfulEndpoint;
import com.adaptris.rest.HttpRestWorkflowServicesConsumer;
import com.adaptris.rest.util.JmxMBeanHelper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class ClusterManagerComponent extends AbstractRestfulEndpoint {


  private static final String BOOTSTRAP_PATH_KEY = "rest.cluster-manager.path";

  private static final String ACCEPTED_FILTER = "GET";

  private static final String DEFAULT_PATH = "/cluster-manager/*";

  private static final String CLUSTER_MANAGER_OBJECT_NAME = "com.adaptris:type=ClusterManager,id=ClusterManager";

  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  private transient JmxMBeanHelper jmxMBeanHelper;

  @Getter(AccessLevel.PROTECTED)
  private transient final String defaultUrlPath = DEFAULT_PATH;

  @Getter(AccessLevel.PROTECTED)
  private transient final String acceptedFilter = ACCEPTED_FILTER;

  public ClusterManagerComponent () {
    super();
    setJmxMBeanHelper(new JmxMBeanHelper());
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message,
      Consumer<AdaptrisMessage> onSuccess, Consumer<AdaptrisMessage> onFailure) {
    try {
      MDC.put(MDC_KEY, friendlyName());

      ClusterManagerMBean clusterManager = getJmxMBeanHelper().proxyMBean(CLUSTER_MANAGER_OBJECT_NAME, ClusterManagerMBean.class);

      final List<ClusterInstance> clusterInstances = new ArrayList<ClusterInstance>();
      clusterManager.getClusterInstances().getKeys().forEach(key -> {
        try {
          clusterInstances.add((ClusterInstance) clusterManager.getClusterInstances().get(key));
        } catch (CoreException e) {}
      });
      String jsonString = new XStreamJsonMarshaller().marshal(clusterInstances);
      message.setContent(jsonString, message.getContentEncoding());

      getConsumer().doResponse(message, message, HttpRestWorkflowServicesConsumer.CONTENT_TYPE_JSON);
      onSuccess.accept(message);

    } catch (Exception ex) {
      getConsumer().doErrorResponse(message, ex, ERROR_DEFAULT);
      onFailure.accept(message);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  @Override
  public void init(Properties config) throws Exception {
    super.init(config);
    setConfiguredUrlPath(config.getProperty(BOOTSTRAP_PATH_KEY));
  }

}
