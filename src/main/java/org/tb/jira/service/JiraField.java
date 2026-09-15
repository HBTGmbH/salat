package org.tb.jira.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * One field as the JIRA field catalogue describes it (#1013). The shape is the same on Server and
 * Cloud for everything the picker shows, so both clients bind to this type.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraField {

  /** The response key — what goes into the configured field list. */
  private String id;

  private String name;

  private boolean custom;

  /** Absent on a few system fields, so never dereference it without checking. */
  private Schema schema;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Schema {

    /** {@code string}, {@code array}, {@code option}, … */
    private String type;

    /**
     * Only on custom fields, and the more telling of the two: the full type key such as
     * {@code com.atlassian.jira.plugin.system.customfieldtypes:cascadingselect}.
     */
    private String custom;
  }
}
