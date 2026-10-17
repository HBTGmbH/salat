package de.hbt.salat.invoice.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import de.hbt.salat.common.util.TicketReferences;
import de.hbt.salat.invoice.domain.InvoiceTimereport;
import de.hbt.salat.jira.domain.JiraTicketInfo;

/**
 * The tickets a set of bookings refers to, and the tickets above them (#1390, #1393), as one
 * invoice sees them.
 *
 * <p>A key is unique within a scope only, not across them (changeset 150). Which ticket a reference
 * names is therefore decided within the branch of the booking: the order-wide scope and every
 * suborder on the path down to the booked one (#1323). Where the key occurs in several of these
 * scopes, it is one ticket and the most recently updated entry counts — the rule the booking
 * suggestions follow (#1025). A ticket that gathers bookings from several branches is resolved over
 * all of them, and so is a parent over the branches of the tickets below it: the branches of a key
 * are those of the bookings that reach it, directly or from below.
 *
 * <p>{@code topLevelKey} is no help here, because the replication resolves the chain per scope and
 * not across the scopes of a branch. The chain is walked here instead; a parent outside the
 * branches ends it, and so does a cycle.
 *
 * <p>Keys are handled in their {@linkplain TicketReferences#comparable comparable} form throughout;
 * free text such as {@code Wartung} is a key like any other that no ticket matches.
 */
final class InvoiceTicketTree {

  /** Keys shaped like a ticket first, by project and then by number — ABC-2 before ABC-10; free text after. */
  static final Comparator<String> KEY_ORDER = comparing((String key) -> !TicketReferences.isKey(key))
      .thenComparing(key -> TicketReferences.isKey(key) ? TicketReferences.projectKeyOf(key) : "")
      .thenComparing(InvoiceTicketTree::numberOf)
      .thenComparing(key -> key.toLowerCase(Locale.ROOT));

  private final Map<String, List<JiraTicketInfo>> ticketsByKey;
  private final Map<Long, Set<Long>> scopesBySuborder;
  /** The suborders booked on that reach the key, directly or from below. */
  private final Map<String, Set<Long>> branchesByKey = new LinkedHashMap<>();
  /** How a booked key was stored, first spelling wins. */
  private final Map<String, String> bookedSpelling = new LinkedHashMap<>();
  private final Map<String, JiraTicketInfo> ticketByKey = new HashMap<>();
  private final Map<String, String> parentByKey = new HashMap<>();

  /**
   * @param bookings         the bookings whose references span the tree
   * @param ticketsByKey     the tickets of all scopes concerned, grouped by comparable key
   * @param scopesBySuborder for every booked suborder the suborders of its branch, itself included
   */
  InvoiceTicketTree(Collection<InvoiceTimereport> bookings, Map<String, List<JiraTicketInfo>> ticketsByKey,
                    Map<Long, Set<Long>> scopesBySuborder) {
    this.ticketsByKey = ticketsByKey;
    this.scopesBySuborder = scopesBySuborder;
    for (var booking : bookings) {
      for (var reference : booking.getTicketReferences()) {
        var key = TicketReferences.comparable(reference);
        bookedSpelling.putIfAbsent(key, reference);
        branchesByKey.computeIfAbsent(key, k -> new HashSet<>()).add(booking.getSuborderId());
      }
    }
    spreadBranchesUpwards();
    branchesByKey.keySet().forEach(key -> resolve(key, branchesByKey.get(key)).ifPresent(ticket -> {
      ticketByKey.put(key, ticket);
      parentOf(key, ticket).ifPresent(parent -> parentByKey.put(key, parent));
    }));
    cutCycles();
  }

  /** Groups the tickets by comparable key — the form the constructor takes them in. */
  static Map<String, List<JiraTicketInfo>> byKey(Collection<JiraTicketInfo> tickets) {
    var result = new HashMap<String, List<JiraTicketInfo>>();
    tickets.forEach(ticket -> result.computeIfAbsent(TicketReferences.comparable(ticket.key()), k -> new ArrayList<>()).add(ticket));
    return result;
  }

  /** Booked keys and the keys above them, booked ones in the order they first occur. */
  Set<String> keys() {
    return branchesByKey.keySet();
  }

  boolean isBooked(String key) {
    return bookedSpelling.containsKey(key);
  }

  Optional<JiraTicketInfo> ticket(String key) {
    return Optional.ofNullable(ticketByKey.get(key));
  }

  /** The key above, {@code null} at the top. */
  String parent(String key) {
    return parentByKey.get(key);
  }

  /** The reference as it was stored, or the key as JIRA spells it for a ticket only reached as a parent. */
  String spelling(String key) {
    var booked = bookedSpelling.get(key);
    if (booked != null) return booked;
    return ticket(key).map(JiraTicketInfo::key).orElse(key);
  }

  /** {@code KEY - summary} when a ticket matches, the reference itself otherwise (#1390). */
  String heading(String key) {
    return ticket(key)
        .filter(ticket -> ticket.summary() != null && !ticket.summary().isBlank())
        .map(ticket -> ticket.key() + " - " + ticket.summary())
        .orElseGet(() -> spelling(key));
  }

  /** The key itself when it is selected, otherwise the nearest selected one above it; {@code null} if none is. */
  String nearestSelected(String key, Set<String> selected) {
    var visited = new HashSet<String>();
    for (var current = key; current != null && visited.add(current); current = parent(current)) {
      if (selected.contains(current)) return current;
    }
    return null;
  }

  /**
   * Hands the branches of every key on to its parent until nothing changes. The sets only grow and
   * are bounded by the booked suborders, so this ends; a parent that cannot be resolved within the
   * branches of the child is not one.
   */
  private void spreadBranchesUpwards() {
    boolean changed = true;
    while (changed) {
      changed = false;
      for (var key : List.copyOf(branchesByKey.keySet())) {
        var branches = branchesByKey.get(key);
        var parent = resolve(key, branches).flatMap(ticket -> parentOf(key, ticket));
        if (parent.isPresent()) {
          changed |= branchesByKey.computeIfAbsent(parent.get(), k -> new HashSet<>()).addAll(branches);
        }
      }
    }
  }

  private Optional<String> parentOf(String key, JiraTicketInfo ticket) {
    if (ticket.parentKey() == null || ticket.parentKey().isBlank()) return Optional.empty();
    var parent = TicketReferences.comparable(ticket.parentKey());
    if (parent.equals(key) || resolve(parent, branchesByKey.get(key)).isEmpty()) return Optional.empty();
    return Optional.of(parent);
  }

  /** The entry of the key that counts within these branches: the most recently updated one. */
  private Optional<JiraTicketInfo> resolve(String key, Set<Long> branches) {
    var scopes = new HashSet<Long>();
    branches.forEach(suborderId -> scopes.addAll(scopesBySuborder.getOrDefault(suborderId, Set.of(suborderId))));
    return ticketsByKey.getOrDefault(key, List.of()).stream()
        .filter(ticket -> ticket.suborderId() == null || scopes.contains(ticket.suborderId()))
        .max(comparing(JiraTicketInfo::updatedTs, nullsFirst(naturalOrder())));
  }

  /** JIRA does not forbid a ticket to end up above itself; where a chain closes, its last edge goes. */
  private void cutCycles() {
    for (var key : branchesByKey.keySet().stream().sorted(KEY_ORDER).toList()) {
      var path = new LinkedHashSet<String>();
      String previous = null;
      for (var current = key; current != null; current = parent(current)) {
        if (!path.add(current)) {
          parentByKey.remove(previous);
          break;
        }
        previous = current;
      }
    }
  }

  private static long numberOf(String key) {
    if (!TicketReferences.isKey(key)) return 0;
    var digits = key.substring(key.lastIndexOf('-') + 1);
    // a number too long for a long has no meaningful order anyway
    return digits.length() > 18 ? Long.MAX_VALUE : Long.parseLong(digits);
  }
}
