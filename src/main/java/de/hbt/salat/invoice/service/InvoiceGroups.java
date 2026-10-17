package de.hbt.salat.invoice.service;

import static java.util.Comparator.comparing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import de.hbt.salat.common.util.TicketReferences;
import de.hbt.salat.invoice.domain.InvoiceGroup;
import de.hbt.salat.invoice.domain.InvoiceLine;
import de.hbt.salat.invoice.domain.InvoiceSuborder;
import de.hbt.salat.invoice.domain.InvoiceTimereport;

/**
 * Sorts the bookings of an invoice into groups by ticket (#1390, #1393).
 *
 * <p>A booking falls to the nearest selected ticket of each of its references: the reference itself,
 * otherwise its parent, otherwise the parent's parent. A reference that reaches a selected ticket that
 * way counts as selected. The duration is divided evenly among the selected references, the remaining
 * minutes going to the first of them; a booking without one goes to „ohne Ticket“ in full. The
 * selection therefore only decides the groups — no minute is lost or counted twice by it.
 *
 * <p>Shares of one booking that fall into the same group make one line there with their sum. A
 * selected ticket gets its group nested in the group of the nearest selected ticket above it.
 */
final class InvoiceGroups {

  private final InvoiceTicketTree tree;
  private final Set<String> selected;
  private final Function<Long, InvoiceSuborder> suborderOf;
  private final Map<String, InvoiceGroup> groupByKey = new LinkedHashMap<>();
  private final Map<InvoiceGroup, String> keyOfGroup = new HashMap<>();
  private final Map<InvoiceGroup, Map<Long, InvoiceLine>> lineByBooking = new HashMap<>();
  private final List<InvoiceGroup> roots = new ArrayList<>();
  private InvoiceGroup withoutTicket;

  private InvoiceGroups(InvoiceTicketTree tree, Set<String> selected, Function<Long, InvoiceSuborder> suborderOf) {
    this.tree = tree;
    this.selected = selected;
    this.suborderOf = suborderOf;
  }

  /**
   * The ticket groups of these bookings, in ticket order, and „ohne Ticket“ last if anything falls to it.
   *
   * @param bookings   in the order their lines are to appear
   * @param selected   comparable keys of the tickets that get a group of their own
   * @param suborderOf the suborder a booking was booked on, by its id
   */
  static List<InvoiceGroup> byTicket(Collection<InvoiceTimereport> bookings, InvoiceTicketTree tree, Set<String> selected,
                                     Function<Long, InvoiceSuborder> suborderOf) {
    var groups = new InvoiceGroups(tree, selected, suborderOf);
    bookings.forEach(groups::add);
    return groups.result();
  }

  /**
   * The duration divided into this many shares: evenly, the remaining minutes to the first (#1390).
   * Seconds do not occur in a booking and are not divided.
   */
  static List<Duration> split(Duration duration, int parts) {
    long minutes = duration.toMinutes();
    long base = minutes / parts;
    var shares = new ArrayList<Duration>();
    shares.add(Duration.ofMinutes(base + minutes % parts));
    for (int i = 1; i < parts; i++) {
      shares.add(Duration.ofMinutes(base));
    }
    return shares;
  }

  private void add(InvoiceTimereport booking) {
    var suborder = suborderOf.apply(booking.getSuborderId());
    var references = new ArrayList<String>();
    var targets = new ArrayList<String>();
    for (var reference : booking.getTicketReferences()) {
      var target = tree.nearestSelected(TicketReferences.comparable(reference), selected);
      if (target != null) {
        references.add(reference);
        targets.add(target);
      }
    }
    if (targets.isEmpty()) {
      if (withoutTicket == null) withoutTicket = InvoiceGroup.withoutTicket();
      withoutTicket.getLines().add(InvoiceLine.whole(booking, suborder));
      return;
    }
    var shares = split(booking.getDuration(), targets.size());
    for (int i = 0; i < targets.size(); i++) {
      var group = groupOf(targets.get(i));
      lineByBooking.computeIfAbsent(group, g -> new HashMap<>())
          .computeIfAbsent(booking.getId(), id -> {
            var line = InvoiceLine.empty(booking, suborder);
            group.getLines().add(line);
            return line;
          })
          .add(shares.get(i), references.get(i));
    }
  }

  /** The group of a selected ticket, created on first use together with the groups above it. */
  private InvoiceGroup groupOf(String key) {
    var existing = groupByKey.get(key);
    if (existing != null) return existing;
    var group = InvoiceGroup.ofTicket(tree.heading(key), !tree.isBooked(key));
    groupByKey.put(key, group);
    keyOfGroup.put(group, key);
    var parent = tree.parent(key) == null ? null : tree.nearestSelected(tree.parent(key), selected);
    if (parent == null) {
      roots.add(group);
    } else {
      groupOf(parent).getChildren().add(group);
    }
    return group;
  }

  private List<InvoiceGroup> result() {
    var result = new ArrayList<>(sorted(roots));
    if (withoutTicket != null) result.add(withoutTicket);
    return result;
  }

  private List<InvoiceGroup> sorted(List<InvoiceGroup> groups) {
    groups.forEach(group -> {
      var children = sorted(group.getChildren());
      group.getChildren().clear();
      group.getChildren().addAll(children);
    });
    return groups.stream().sorted(comparing(keyOfGroup::get, InvoiceTicketTree.KEY_ORDER)).toList();
  }
}
