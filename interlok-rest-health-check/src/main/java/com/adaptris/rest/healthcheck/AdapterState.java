package com.adaptris.rest.healthcheck;

import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@XStreamAlias("adapter-state")
@NoArgsConstructor
public class AdapterState extends State {

  // Defaults to "null" to avoid [""] as the output; better for it to be not present rather than
  // the wrong type?
  @Getter
  @Setter
  private List<ChannelState> channelStates;

  public AdapterState withChannelStates(List<ChannelState> states) {
    setChannelStates(states);
    return this;
  }

  public List<ChannelState> applyDefaultIfNull() {
    if (getChannelStates() == null) {
      return withChannelStates(new ArrayList<>()).getChannelStates();
    }
    return getChannelStates();
  }

}