package com.adaptris.rest.util;

import java.util.Set;

import javax.management.ObjectName;

public interface DummyMBean {

  String DUMMY_MBEAN_BASE = "com.adaptris:type=Dummy,name=%s";
  String DUMMY_MBEAN_CHILD_BASE = "com.adaptris:type=Dummy,name=%s,child=%s";

  String getUniqueId();

  Set<ObjectName> getChildren();

}
