package de.hbt.salat.jira.service;

import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import de.hbt.salat.jira.domain.JiraFieldConfig;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.domain.ResolvedFieldValue;

/**
 * The values of a ticket nobody enters (#881, #1386): the top-level key and the inherited fields,
 * derived from the parent chain within one scope. Computed for every ticket of the scope, whoever
 * maintains it — when a replication runs and when a ticket is written by hand — so that both ways
 * arrive at the same values.
 *
 * <p>Which fields are inherited follows from the scope, not from the one writing: the inherited fields
 * of every replication of the scope, together with those an import marked as inherited. The latter
 * are stored nowhere else than in their result, the {@code custom_fields_effective} of the tickets
 * maintained by hand.
 */
final class JiraTicketChains {

  private JiraTicketChains() {
  }

  /**
   * The fields resolved along the parent chain in a scope.
   *
   * @param configsOfScope the replications of exactly this scope
   * @param tickets the tickets of the scope
   */
  static Set<String> inheritedFields(Collection<JiraReplicationConfig> configsOfScope, Collection<JiraTicket> tickets) {
    var fields = new LinkedHashSet<String>();
    configsOfScope.forEach(config -> fields.addAll(JiraFieldConfig.from(config).inheritedFieldPaths()));
    tickets.stream()
        .filter(ticket -> !ticket.isReplicated() && ticket.getCustomFieldsEffective() != null)
        .forEach(ticket -> fields.addAll(ticket.getCustomFieldsEffective().keySet()));
    return fields;
  }

  /**
   * Writes top-level key and inherited fields of every ticket of the scope; an own value beats an
   * inherited one, and the nearest ancestor with a value wins. The chain stays within the scope and
   * stops at a cycle, which nothing in JIRA or in a column written by hand rules out.
   *
   * @return the tickets whose values changed, to be saved
   */
  static List<JiraTicket> resolve(Collection<JiraTicket> tickets, Set<String> inheritedFields) {
    Map<String, JiraTicket> byKey = tickets.stream()
        .collect(Collectors.toMap(JiraTicket::getKey, identity(), (first, second) -> first));
    var changed = new ArrayList<JiraTicket>();
    for (var ticket : tickets) {
      var effective = new LinkedHashMap<String, ResolvedFieldValue>();
      for (var field : inheritedFields) {
        var own = storedValue(ticket, field);
        if (own != null) effective.put(field, new ResolvedFieldValue(own, null));
      }
      var visited = new HashSet<String>();
      visited.add(ticket.getKey());
      var top = ticket;
      while (top.getParentKey() != null && visited.add(top.getParentKey()) && byKey.containsKey(top.getParentKey())) {
        top = byKey.get(top.getParentKey());
        for (var field : inheritedFields) {
          if (effective.containsKey(field)) continue;
          var value = storedValue(top, field);
          if (value != null) effective.put(field, new ResolvedFieldValue(value, top.getKey()));
        }
      }
      // Absent rather than empty: a json column cannot hold an empty string, and JSON_EXTRACT on NULL
      // answers NULL instead of aborting the statement around it.
      var resolved = effective.isEmpty() ? null : effective;
      if (Objects.equals(top.getKey(), ticket.getTopLevelKey())
          && Objects.equals(resolved, ticket.getCustomFieldsEffective())) continue;
      ticket.setTopLevelKey(top.getKey());
      ticket.setCustomFieldsEffective(resolved);
      changed.add(ticket);
    }
    return changed;
  }

  private static String storedValue(JiraTicket ticket, String field) {
    var customFields = ticket.getCustomFields();
    return customFields != null ? customFields.get(field) : null;
  }
}
