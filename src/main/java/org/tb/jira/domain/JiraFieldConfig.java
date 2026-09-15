package org.tb.jira.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.tb.common.exception.TechnicalException;

/**
 * The additional JIRA fields one replication config asks for (#881), parsed once per run.
 *
 * <p>The module stays free of any customer meaning: an entry is a response key, and naming it in
 * business terms happens outside, in a view.
 *
 * <p>A key is not restricted to custom fields. A standard field is configured the same way and
 * arrives the same way — {@code status}, {@code priority}, {@code resolution}, {@code assignee},
 * {@code components}, {@code duedate}. The fields the replication has its own columns for —
 * {@code summary}, {@code issuetype}, {@code labels}, {@code created}, {@code updated},
 * {@code parent} — are fetched anyway; naming one here additionally stores it in the JSON as well,
 * which is a duplicate rather than an error.
 *
 * <p>An entry may address a part of a field by a dotted path: {@code customfield_10200.child.value}
 * for the second level of a cascading select, {@code assignee.emailAddress} for a property other
 * than the one the default order would pick. JIRA itself only accepts top-level ids in its
 * {@code fields} parameter, so the head before the first dot is what gets requested and the rest is
 * walked in {@code JiraFieldValues}. A wrong head shows up in the log because no answer carries it;
 * a wrong tail cannot be told apart from a field that is simply not set and stays silent.
 *
 * @param fieldPaths every entry to read and to store, as configured — the union of the two lists. An
 *     inherited entry that somebody forgot to also list as an additional one would otherwise never
 *     be requested and stay silently empty. These are also the keys of
 *     {@link JiraTicket#getCustomFields()}, path and all.
 * @param inheritedFieldPaths the entries whose value is resolved along the parent chain
 * @param hash identifies the configured lists, {@code null} when nothing is configured. Built from
 *     the configured values (trimmed, sorted), not from the stored JSON — MySQL normalises JSON on
 *     write, so the stored document is not a stable input. A {@code null} hash for an unused feature
 *     keeps the tickets of every config that does not use it untouched.
 */
public record JiraFieldConfig(List<String> fieldPaths, List<String> inheritedFieldPaths, String hash) {

  private static final JiraFieldConfig NONE = new JiraFieldConfig(List.of(), List.of(), null);

  public static JiraFieldConfig from(JiraReplicationConfig config) {
    var inherited = split(config.getInheritedFieldNames());
    var paths = new TreeSet<>(split(config.getAdditionalFieldNames()));
    paths.addAll(inherited);
    if (paths.isEmpty()) {
      return NONE;
    }
    return new JiraFieldConfig(List.copyOf(paths), List.copyOf(inherited), hash(paths, inherited));
  }

  public boolean isEmpty() {
    return fieldPaths.isEmpty();
  }

  /**
   * The top-level field ids to ask JIRA for. Several paths into the same field — the two levels of a
   * cascading select, say — are one request key.
   */
  public List<String> requestKeys() {
    return fieldPaths.stream().map(JiraFieldConfig::headOf).distinct().toList();
  }

  private static String headOf(String path) {
    var dot = path.indexOf('.');
    return dot < 0 ? path : path.substring(0, dot);
  }

  /**
   * Splits the configured list. An entry with an empty segment — a stray or trailing dot — is
   * dropped: it can only ever address nothing, and keeping it would put a key into the stored JSON
   * that never carries a value.
   */
  private static SortedSet<String> split(String commaSeparated) {
    var result = new TreeSet<String>();
    if (commaSeparated == null || commaSeparated.isBlank()) {
      return result;
    }
    for (var entry : commaSeparated.split(",")) {
      var segments = entry.trim().split("\\.", -1);
      var cleaned = new StringBuilder();
      var usable = true;
      for (var segment : segments) {
        var trimmed = segment.trim();
        if (trimmed.isEmpty()) {
          usable = false;
          break;
        }
        if (!cleaned.isEmpty()) cleaned.append('.');
        cleaned.append(trimmed);
      }
      if (usable && !cleaned.isEmpty()) {
        result.add(cleaned.toString());
      }
    }
    return result;
  }

  private static String hash(Collection<String> paths, Collection<String> inherited) {
    // Both lists take part: moving a field into the inherited list changes what the effective
    // column holds, even though the requested set stays the same.
    var input = String.join(",", paths) + ";" + String.join(",", inherited);
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new TechnicalException("SHA-256 not available", e);
    }
  }
}
