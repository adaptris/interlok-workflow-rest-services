package com.adaptris.rest;

import static com.adaptris.rest.AbstractRestfulEndpoint.MANAGEMENT_PORT_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

public class ManagementPortConfigTest {

  private static final String ENABLED_KEY = "rest.test.management-port.enabled";

  @Test
  public void testNotConfigured() {
    assertNull(AbstractRestfulEndpoint.managementPort(new Properties(), ENABLED_KEY));
  }

  @Test
  public void testBlank() {
    assertNull(AbstractRestfulEndpoint.managementPort(props(" ", null), ENABLED_KEY));
  }

  @Test
  public void testConfigured() {
    assertEquals(8081, AbstractRestfulEndpoint.managementPort(props("8081", null), ENABLED_KEY));
  }

  @Test
  public void testConfigured_Whitespace() {
    assertEquals(8081, AbstractRestfulEndpoint.managementPort(props(" 8081 ", null), ENABLED_KEY));
  }

  @Test
  public void testBoundaries() {
    assertEquals(1, AbstractRestfulEndpoint.managementPort(props("1", null), ENABLED_KEY));
    assertEquals(65535, AbstractRestfulEndpoint.managementPort(props("65535", null), ENABLED_KEY));
  }

  @Test
  public void testConfigured_ExplicitlyEnabled() {
    assertEquals(8081, AbstractRestfulEndpoint.managementPort(props("8081", "true"), ENABLED_KEY));
  }

  @Test
  public void testConfigured_OptedOut() {
    assertNull(AbstractRestfulEndpoint.managementPort(props("8081", "false"), ENABLED_KEY));
  }

  @Test
  public void testConfigured_UnrecognisedEnabledValue_StaysOnMainPort() {
    assertNull(AbstractRestfulEndpoint.managementPort(props("8081", "flase"), ENABLED_KEY));
  }

  @Test
  public void testOptedOut_NoPort() {
    assertNull(AbstractRestfulEndpoint.managementPort(props(null, "false"), ENABLED_KEY));
    assertNull(AbstractRestfulEndpoint.managementPort(props(null, "true"), ENABLED_KEY));
  }

  @Test
  public void testInvalidPort() {
    for (String value : new String[] { "abc", "0", "-1", "65536", "70000", "8081x", "80.81" }) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
          () -> AbstractRestfulEndpoint.managementPort(props(value, null), ENABLED_KEY), value);
      assertTrue(e.getMessage().contains(MANAGEMENT_PORT_KEY), e.getMessage());
      assertTrue(e.getMessage().contains(value), e.getMessage());
    }
  }

  @Test
  public void testInvalidPort_EvenIfOptedOut() {
    // A badly configured port is a configuration error regardless of whether this component uses it.
    assertThrows(IllegalArgumentException.class, () -> AbstractRestfulEndpoint.managementPort(props("abc", "false"), ENABLED_KEY));
  }

  private static Properties props(String port, String enabled) {
    Properties p = new Properties();
    if (port != null) {
      p.setProperty(MANAGEMENT_PORT_KEY, port);
    }
    if (enabled != null) {
      p.setProperty(ENABLED_KEY, enabled);
    }
    return p;
  }

}
