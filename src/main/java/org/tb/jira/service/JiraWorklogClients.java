package org.tb.jira.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.jira.domain.JiraApiFlavor;

/** Hands out the {@link JiraWorklogClient} for a configured {@link JiraApiFlavor}. */
@Component
public class JiraWorklogClients {

  private final Map<JiraApiFlavor, JiraWorklogClient> clientsByFlavor;

  public JiraWorklogClients(List<JiraWorklogClient> clients) {
    clientsByFlavor = new EnumMap<>(JiraApiFlavor.class);
    clients.forEach(client -> clientsByFlavor.put(client.flavor(), client));
  }

  public JiraWorklogClient forFlavor(JiraApiFlavor flavor) {
    var client = clientsByFlavor.get(flavor);
    if (client == null) {
      throw new IllegalArgumentException("No JIRA worklog client for API flavor " + flavor);
    }
    return client;
  }
}
