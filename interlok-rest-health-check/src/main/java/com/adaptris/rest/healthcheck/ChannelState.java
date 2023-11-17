package com.adaptris.rest.healthcheck;

import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@XStreamAlias("channel-state")
@NoArgsConstructor
public class ChannelState extends State {

  // Defaults to "null" to avoid [""] as the output; better for it to be not present rather than
  // the wrong type?
  @Getter
  @Setter
  private List<WorkflowState> workflowStates;

  public ChannelState withWorkflowStates(List<WorkflowState> states) {
    setWorkflowStates(states);
    return this;
  }

  public List<WorkflowState> applyDefaultIfNull() {
    if (getWorkflowStates() == null) {
      return withWorkflowStates(new ArrayList<>()).getWorkflowStates();
    }
    return getWorkflowStates();
  }

}
