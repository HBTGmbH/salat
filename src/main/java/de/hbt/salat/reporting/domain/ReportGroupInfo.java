package de.hbt.salat.reporting.domain;

import java.util.Comparator;

/**
 * A report group as the pages show it (#1523): a personal group by the name of its person today.
 *
 * @param id       {@code null} for the reports without a group, which the pages show as a group of their own —
 *                 and for a personal group that is not created yet
 * @param name     the name of the group, or of the person whose personal group it is; {@code null} without a group
 * @param personal whether it is the personal group of a people lead
 */
public record ReportGroupInfo(Long id, String name, boolean personal) {

  public static final ReportGroupInfo NONE = new ReportGroupInfo(null, null, false);

  /** Alphabetically, case aside, and the reports without a group last. */
  public static final Comparator<ReportGroupInfo> ORDER = Comparator
      .comparing(ReportGroupInfo::isNone)
      .thenComparing(group -> group.name() == null ? "" : group.name(), String.CASE_INSENSITIVE_ORDER)
      .thenComparing(group -> group.id() == null ? 0L : group.id());

  public boolean isNone() {
    return id == null && !personal;
  }

  /** What a group is called in a file name or a list of titles: the reports without one have no name. */
  public String qualify(String title) {
    return name == null ? title : name + ": " + title;
  }
}
