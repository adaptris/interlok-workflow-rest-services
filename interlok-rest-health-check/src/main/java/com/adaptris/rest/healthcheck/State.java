package com.adaptris.rest.healthcheck;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
public abstract class State {

  @Getter
  @Setter
  private String id;
  @Getter
  @Setter
  private String state;

  @SuppressWarnings("unchecked")
  public <T extends State> T withId(String s) {
    setId(s);
    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public <T extends State> T withState(String s) {
    setState(s);
    return (T) this;
  }

}
