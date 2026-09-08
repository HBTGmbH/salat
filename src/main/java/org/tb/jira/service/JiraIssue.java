package org.tb.jira.service;

import java.util.Map;
import lombok.Data;

/**
 * One issue as returned by a JIRA search. The shape is the same on Server and Cloud for everything
 * the replication reads, so both clients bind to this type.
 */
@Data
public class JiraIssue {

  private String id; // numeric string
  private String key;
  private Map<String, Object> fields;
}
