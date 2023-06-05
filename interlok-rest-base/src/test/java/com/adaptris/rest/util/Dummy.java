package com.adaptris.rest.util;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.management.ObjectName;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class Dummy implements DummyMBean {

  @Getter
  private final String uniqueId = UUID.randomUUID().toString();

  @Getter
  @Setter(AccessLevel.PRIVATE)
  private Set<ObjectName> children;

  public Dummy(int count) throws Exception {
    Set<ObjectName> offspring = new HashSet<>();
    for (int i = 0; i < count; i++) {
      String childRef = String.format(DUMMY_MBEAN_CHILD_BASE, getUniqueId(), UUID.randomUUID().toString());
      offspring.add(ObjectName.getInstance(childRef));
    }
    setChildren(offspring);
  }

}
