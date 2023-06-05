package com.adaptris.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.interlok.client.MessageTarget;

public class JettyConsumerWorkflowTargetTranslatorTest {

  private static final String PATH_KEY = "jettyURI";

  private AdaptrisMessage message;

  private JettyConsumerWorkflowTargetTranslator targetTranslator;

  @BeforeEach
  public void setUp() throws Exception {
    targetTranslator = new JettyConsumerWorkflowTargetTranslator();
    message = DefaultMessageFactory.getDefaultInstance().newMessage();
  }

  @Test
  public void testFullPathToWorkflow() throws Exception {
    message.addMessageHeader(PATH_KEY, "/workflow-services/myAdapter/myChannel/myWorkflow");
    MessageTarget target = targetTranslator.translateTarget(message);

    assertEquals("myAdapter", target.getAdapter());
    assertEquals("myChannel", target.getChannel());
    assertEquals("myWorkflow", target.getWorkflow());
  }

  @Test
  public void testRootReturnsNull() throws Exception {
    message.addMessageHeader(PATH_KEY, "/workflow-services/");
    MessageTarget target = targetTranslator.translateTarget(message);

    assertNull(target);
  }

  @Test
  public void testNoPathMetadataReturnsNull() throws Exception {
    MessageTarget target = targetTranslator.translateTarget(message);

    assertNull(target);
  }

  @Test
  public void testWrongNumberOfParams() throws Exception {
    message.addMessageHeader(PATH_KEY, "/workflow-services/1/2/3/4/5/6/7/");
    try {
      targetTranslator.translateTarget(message);
      fail("Message target should be null, because one was not supplied.");
    } catch (CoreException ex) {
      // expected
    }
  }

}
