package com.adaptris.rest.util;

import java.util.Set;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import com.adaptris.core.util.JmxHelper;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Synchronized;

@NoArgsConstructor
public class JmxMBeanHelper {

  @Getter(AccessLevel.PRIVATE)
  @Setter(AccessLevel.PRIVATE)
  private MBeanServer mBeanServer;

  private transient final Object mbeanLock = new Object[0];

  public String getStringAttribute(String objectName, String attributeName) throws Exception {
    return (String) mBeanServer().getAttribute(new ObjectName(objectName), attributeName);
  }

  public String getStringAttributeClassName(String objectName, String attributeName) throws Exception {
    return mBeanServer().getAttribute(new ObjectName(objectName), attributeName).getClass().getSimpleName();
  }

  public <T> T proxyMBean(String objectNameString, Class<T> type) throws MalformedObjectNameException {
    return proxyMBean(new ObjectName(objectNameString), type);
  }

  public <T> T proxyMBean(ObjectName objectName, Class<T> type) {
    return JMX.newMBeanProxy(mBeanServer(), objectName, type, true);
  }

  @SuppressWarnings("unchecked")
  public Set<ObjectName> getObjectSetAttribute(String objectName, String attributeName) throws Exception {
    return (Set<ObjectName>) mBeanServer().getAttribute(new ObjectName(objectName), attributeName);
  }

  public Set<ObjectInstance> getMBeans(String objectNameQuery) throws Exception {
    return mBeanServer().queryMBeans(new ObjectName(objectNameQuery), null);
  }

  public Set<ObjectName> getMBeanNames(String objectNameQuery) throws Exception {
    return mBeanServer().queryNames(new ObjectName(objectNameQuery), null);
  }

  @Synchronized("mbeanLock")
  protected MBeanServer mBeanServer() {
    if (getMBeanServer() == null)
      setMBeanServer(JmxHelper.findMBeanServer());

    return getMBeanServer();
  }

}
