package com.adaptris.rest.healthcheck;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ChannelStateTest {

  @Test
  public void testWithWorkflowStates() {
    ChannelState state = new ChannelState().withId("id").withState("state");
    assertNull(state.getWorkflowStates());
    state.withWorkflowStates(new ArrayList<>());
    assertNotNull(state.getWorkflowStates());
  }

  @Test
  public void testApplyDefaultIfNull() {
    ChannelState state = new ChannelState();
    assertNull(state.getWorkflowStates());

    List<WorkflowState> s1 = state.applyDefaultIfNull();
    List<WorkflowState> s2 = state.applyDefaultIfNull();
    assertSame(s1, s2);
    assertNotNull(state.getWorkflowStates());
  }

}
