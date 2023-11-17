package com.adaptris.rest.healthcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.adaptris.core.XStreamJsonMarshaller;

public class AdapterListTest {

  @Test
  public void testSize() {
    List<AdapterState> list = build();
    AdapterList adapterList = AdapterList.wrap(list);
    assertEquals(1, adapterList.size());
  }

  @Test
  public void testGetInt() {
    List<AdapterState> list = build();
    AdapterList adapterList = AdapterList.wrap(list);
    assertEquals(1, adapterList.size());
    assertNotNull(adapterList.get(0));
  }

  @Test
  public void testIterator() {
    List<AdapterState> list = build();
    AdapterList adapterList = AdapterList.wrap(list);
    assertEquals(1, adapterList.size());
    assertNotNull(adapterList.iterator());
    assertEquals(AdapterState.class, adapterList.iterator().next().getClass());
  }

  @Test
  public void testAddAdapterState() throws Exception {
    AdapterList adapterList = new AdapterList();
    adapterList.add(new AdapterState().withId("adapter1").withState("ClosedState"));
    adapterList.add(adapterList.size(), new AdapterState().withId("adapter2").withState("ClosedState"));
    assertEquals(2, adapterList.size());
    System.err.println(new XStreamJsonMarshaller().marshal(adapterList));
  }

  private List<AdapterState> build() {
    List<WorkflowState> c1ws = new ArrayList<>(Arrays.asList(new WorkflowState().withId("c1w1").withState("StartedState"),
        new WorkflowState().withId("c1w2").withState("StartedState")));
    List<WorkflowState> c2ws = new ArrayList<>(Arrays.asList(new WorkflowState().withId("c2w1").withState("StartedState"),
        new WorkflowState().withId("c2w2").withState("StartedState")));
    List<ChannelState> channelStates = new ArrayList<>(
        Arrays.asList((ChannelState) new ChannelState().withWorkflowStates(c1ws).withId("c1").withState("StartedState"),
            (ChannelState) new ChannelState().withWorkflowStates(c2ws).withId("c2").withState("StartedState")));

    List<AdapterState> list = Arrays
        .asList(new AdapterState().withChannelStates(channelStates).withId("MyAdapter").withState("StartedState"));
    return new ArrayList<>(list);
  }

}
