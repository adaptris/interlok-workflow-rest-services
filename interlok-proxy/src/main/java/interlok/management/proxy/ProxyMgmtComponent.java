package interlok.management.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import com.adaptris.core.StandaloneConsumer;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.LifecycleHelper;

/**
 * Management Component that allows you to proxy various HTTP requests internally.
 * 
 * <p>
 * The use case for this is something like Heroku which generally only allows you to expose a single
 * external port. If you have an existing API endpoint listening via <i>embedded jetty</i> and you
 * have also require <i>jolokia</i> then you need to jump through additional hoops to make this
 * possible (it could be done by having an additional API endpoint workflow that proxies everything
 * to the jolokia endpoint). This is a very simplified proxy simply passes through all headers as-is
 * to the proxied URL.
 * </p>
 * 
 * <p>
 * If we take the following configuration (note the use of <b>::</b> as the separator) :-
 * 
 * <pre>
 * {@code
 interlok.proxy.1=/jolokia::http://localhost:8081
 interlok.proxy.2=/alternate::http://my.other.host:8082
 * }
 * </pre>
 * 
 * In this case we would register as a listener against the URI <i>/jolokia/*</i> and
 * <i>/alternate/*</i> against the embedded jetty component. All traffic to our registered URI (e.g.
 * http://localhost:8080/jolokia/my/jolokia/action) would be forwarded to the corresponding
 * server/port combination (http://localhost:8081/jolokia/my/jolokia/action).
 * </p>
 */
// Since this supports multiple "tunnels" in the same way that sshtunnel does
// we don't extend AbstractRestfulEndpoint at all, we have to make our own...
// It effectively does this : https://interlok.adaptris.net/blog/2017/09/04/interlok-proxy.html
// in code.
public class ProxyMgmtComponent extends MgmtComponentImpl {

  private transient List<StandaloneConsumer> proxyWorkers = new ArrayList<>();

  @Override
  public void init(Properties config) throws Exception {
    proxyWorkers = build(config);
  }

  @Override
  public void start() throws Exception {
    for (StandaloneConsumer c : proxyWorkers) {
      LifecycleHelper.initAndStart(c);
    }
    log.debug("ProxyManagementComponent component started.");    
  }

  @Override
  public void stop() throws Exception {
    for (StandaloneConsumer c : proxyWorkers) {
      LifecycleHelper.stopAndClose(c);
    }
    log.debug("ProxyManagementComponent component stopped.");
    
  }

  @Override
  public void destroy() throws Exception {
    proxyWorkers.clear();
  }

  protected List<StandaloneConsumer> build(Properties config) {
    return Helper.build(config);
  }
}
