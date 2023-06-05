package com.adaptris.rest.healthcheck;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import lombok.Getter;
import lombok.Setter;

/**
 * List of adapters that will be returned by the healthcheck
 *
 */
@XStreamAlias("adapters")
public class AdapterList extends AbstractList<AdapterState> {

  @Getter
  @Setter
  @XStreamImplicit(itemFieldName = "adapter-state")
  private List<AdapterState> adapterStates;

  public AdapterList() {
    setAdapterStates(new ArrayList<>());
  }

  public static AdapterList wrap(List<AdapterState> states) {
    AdapterList list = new AdapterList();
    list.setAdapterStates(states);
    return list;
  }

  @Override
  public AdapterState get(int index) {
    return getAdapterStates().get(index);
  }

  @Override
  public int size() {
    return getAdapterStates().size();
  }

  @Override
  public Iterator<AdapterState> iterator() {
    return getAdapterStates().iterator();
  }

  @Override
  public boolean add(AdapterState x) {
    return getAdapterStates().add(x);
  }

  @Override
  public void add(int index, AdapterState x) {
    getAdapterStates().add(index, x);
  }

}