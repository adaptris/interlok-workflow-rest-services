package com.adaptris.rest.util;

import static com.adaptris.rest.util.DummyMBean.DUMMY_MBEAN_BASE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.junit.jupiter.api.Test;

public class JmxHelperTest {

  private static final String MY_SEARCH_CRITERIA = "com.adaptris:type=Dummy,*";
  private static final String UID_ATTR = "UniqueId";
  private static final String CHILDREN_ATTR = "Children";

  @Test
  public void testGetStringAttribute() throws Exception {
    JmxMBeanHelper helper = new JmxMBeanHelper();
    MBeanServer server = helper.mBeanServer();
    Dummy dummy = new Dummy(0);
    String objectRef = String.format(DUMMY_MBEAN_BASE, dummy.getUniqueId());
    try {
      server.registerMBean(dummy, ObjectName.getInstance(objectRef));
      assertEquals(dummy.getUniqueId(), helper.getStringAttribute(objectRef, UID_ATTR));
    } finally {
      server.unregisterMBean(ObjectName.getInstance(objectRef));
    }
  }

  @Test
  public void testGetStringAttributeClassName() throws Exception {
    JmxMBeanHelper helper = new JmxMBeanHelper();
    MBeanServer server = helper.mBeanServer();
    Dummy dummy = new Dummy(0);
    String objectRef = String.format(DUMMY_MBEAN_BASE, dummy.getUniqueId());
    try {
      server.registerMBean(dummy, ObjectName.getInstance(objectRef));
      assertEquals("String", helper.getStringAttributeClassName(objectRef, UID_ATTR));
    } finally {
      server.unregisterMBean(ObjectName.getInstance(objectRef));
    }
  }

  @Test
  public void testProxyMBean() throws Exception {
    JmxMBeanHelper helper = new JmxMBeanHelper();
    MBeanServer server = helper.mBeanServer();
    Dummy dummy = new Dummy(0);
    String objectRef = String.format(DUMMY_MBEAN_BASE, dummy.getUniqueId());
    try {
      server.registerMBean(dummy, ObjectName.getInstance(objectRef));
      DummyMBean mbean = helper.proxyMBean(objectRef, DummyMBean.class);
      assertNotNull(mbean);
      assertEquals(dummy.getUniqueId(), mbean.getUniqueId());
    } finally {
      server.unregisterMBean(ObjectName.getInstance(objectRef));
    }
  }

  @Test
  public void testGetObjectSetAttribute() throws Exception {
    JmxMBeanHelper helper = new JmxMBeanHelper();
    MBeanServer server = helper.mBeanServer();
    Dummy dummy = new Dummy(10);
    String objectRef = String.format(DUMMY_MBEAN_BASE, dummy.getUniqueId());
    try {
      server.registerMBean(dummy, ObjectName.getInstance(objectRef));
      Set<ObjectName> set = helper.getObjectSetAttribute(objectRef, CHILDREN_ATTR);
      assertEquals(10, set.size());
    } finally {
      server.unregisterMBean(ObjectName.getInstance(objectRef));
    }
  }

  @Test
  public void testGetMBeans() throws Exception {
    JmxMBeanHelper helper = new JmxMBeanHelper();
    MBeanServer server = helper.mBeanServer();
    Dummy dummy = new Dummy(0);
    String objectRef = String.format(DUMMY_MBEAN_BASE, dummy.getUniqueId());
    try {
      server.registerMBean(dummy, ObjectName.getInstance(objectRef));
      Set<ObjectInstance> set = helper.getMBeans(MY_SEARCH_CRITERIA);
      assertEquals(1, set.size());
    } finally {
      server.unregisterMBean(ObjectName.getInstance(objectRef));
    }
  }

  @Test
  public void testGetMBeanNames() throws Exception {
    JmxMBeanHelper helper = new JmxMBeanHelper();
    MBeanServer server = helper.mBeanServer();
    Dummy dummy = new Dummy(0);
    String objectRef = String.format(DUMMY_MBEAN_BASE, dummy.getUniqueId());
    try {
      server.registerMBean(dummy, ObjectName.getInstance(objectRef));
      Set<ObjectName> set = helper.getMBeanNames(MY_SEARCH_CRITERIA);
      assertEquals(1, set.size());
    } finally {
      server.unregisterMBean(ObjectName.getInstance(objectRef));
    }
  }

}
