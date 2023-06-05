package com.adaptris.rest;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.CoreException;
import com.adaptris.interlok.client.MessageTarget;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class JettyConsumerWorkflowTargetTranslator implements WorkflowTargetTranslator {

  /**
   * The metadata item that contains the full rest path given by the user
   *
   * For example it could be "/workflow-services/myAdapter/myChannel/myWorkflow"
   */
  private static final String PATH_KEY = "jettyURI";

  @Override
  public MessageTarget translateTarget(AdaptrisMessage message) throws CoreException {
    String metadataValue = message.getMetadataValue(PATH_KEY);
    if (metadataValue == null) {
      return null;
    } else {
      String[] pathItems = metadataValue.split("/");
      if (pathItems.length == 5) { // We expect 5 items like the example above.
        MessageTarget result = new MessageTarget().withAdapter(pathItems[2]).withChannel(pathItems[3]).withWorkflow(pathItems[4]);

        return result;
      } else if (pathItems.length == 2) { // request is
        return null;
      } else { // request is
        throw new CoreException("Could not determine your target workflow.");
      }

    }
  }

}
