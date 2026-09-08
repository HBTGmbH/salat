package org.tb.jira.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.jira.domain.JiraApiFlavor;

/** Hands out the {@link JiraSearchClient} for a configured {@link JiraApiFlavor}. */
@Component
public class JiraSearchClients {

  private final Map<JiraApiFlavor, JiraSearchClient> clientsByFlavor;

  public JiraSearchClients(List<JiraSearchClient> clients) {
    clientsByFlavor = new EnumMap<>(JiraApiFlavor.class);
    clients.forEach(client -> clientsByFlavor.put(client.flavor(), client));
  }

  public JiraSearchClient forFlavor(JiraApiFlavor flavor) {
    var client = clientsByFlavor.get(flavor);
    if (client == null) {
      throw new IllegalArgumentException("No JIRA search client for API flavor " + flavor);
    }
    return client;
  }
}
