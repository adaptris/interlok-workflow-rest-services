package interlok.management.proxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import com.adaptris.core.StandaloneConsumer;

public class ProxyComponentTest {

  @Test
  public void testLifecycle() throws Exception {
    ProxyMgmtComponent comp = new ProxyMgmtComponent();
    
    comp.init(new Properties());
    comp.start();
    comp.stop();
    comp.destroy();
  }
  
  @Test
  public void testLifecycle_Dummy() throws Exception {
    ProxyMgmtComponent comp = new ProxyOverride();
    
    comp.init(new Properties());
    comp.start();
    comp.stop();
    comp.destroy();
  }
  
  @SuppressWarnings({"unchecked", "rawtypes"})
  private class ProxyOverride extends ProxyMgmtComponent {
    @Override
    protected List<StandaloneConsumer> build(Properties config) {
      return new ArrayList(Arrays.asList(new StandaloneConsumer()));
    } 
  }
}
