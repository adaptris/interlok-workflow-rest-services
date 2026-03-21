package com.adaptris.rest.healthcheck;

import com.thoughtworks.xstream.annotations.XStreamAlias;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@XStreamAlias("connection-state")
@NoArgsConstructor
public class ConnectionState extends State {

  @Getter
  @Setter
  private String parentType;

  @Getter
  @Setter
  private String parentId;

  public ConnectionState withParentType(String parentType) {
    setParentType(parentType);
    return this;
  }

  public ConnectionState withParentId(String parentId) {
    setParentId(parentId);
    return this;
  }

}
