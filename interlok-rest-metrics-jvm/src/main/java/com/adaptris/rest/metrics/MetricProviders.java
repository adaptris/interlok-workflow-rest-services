package com.adaptris.rest.metrics;

import java.util.ArrayList;
import java.util.List;

public class MetricProviders {

  private static final List<MetricBinder> PROVIDERS = new ArrayList<>();

  private MetricProviders() {
  }

  public static List<MetricBinder> getProviders() {
    return PROVIDERS;
  }

  public static void addProvider(MetricBinder provider) {
    getProviders().add(provider);
  }

}
