package de.hbt.salat.reporting.persistence;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Where the report definitions go when report groups replace the prefix in their name (#1523), decided
 * without the database so that the rules can be tested on invented examples. {@link ReportGroupMigration}
 * reads the rows and writes the plan.
 *
 * <ul>
 *   <li>The prefix is everything up to the first colon, the title what follows it without the blanks in
 *   between. A name with nothing before or after the colon has no prefix.</li>
 *   <li>A report whose owner is a people lead and not a manager (status {@code pv}) goes into the personal
 *   group of its owner and loses its prefix, whatever the prefix says — it was often a personal sign.</li>
 *   <li>Every other prefix becomes a group. Prefixes that differ in case only are one group, spelled as
 *   most of its reports spell it; on a tie as the oldest report does.</li>
 *   <li>A report without a prefix stands without a group, unless its owner is a people lead.</li>
 *   <li>Where two titles of a group would be the same, case aside, the younger report keeps its name as it
 *   was — the oldest one first gets the title. Should that name be taken as well, its id is appended.</li>
 * </ul>
 */
public final class ReportGroupMigrationPlan {

  private ReportGroupMigrationPlan() {
  }

  /** A report definition as the migration reads it. */
  public record Row(long id, String name, Long ownerUserId, boolean ownerIsPeopleLead, LocalDateTime created) {
  }

  /** Where a report goes: a group managers maintain (by name), a personal group (by owner), or none. */
  public record Target(String groupName, Long ownerUserId) {

    static final Target NONE = new Target(null, null);

    public boolean isNone() {
      return groupName == null && ownerUserId == null;
    }
  }

  /** A report with the title it gets and the group it goes into. */
  public record Placement(long id, String title, Target target) {
  }

  /**
   * @param groupNames      the groups managers maintain, to be created, in the order of their names
   * @param personalOwners  the logins that get a personal group
   */
  public record Plan(List<String> groupNames, List<Long> personalOwners, List<Placement> placements) {
  }

  private record Split(String prefix, String title) {
  }

  private static final Comparator<Row> OLDEST_FIRST =
      comparing(Row::created, nullsFirst(naturalOrder())).thenComparing(Row::id);

  public static Plan plan(List<Row> rows) {
    var oldestFirst = rows.stream().sorted(OLDEST_FIRST).toList();
    var splits = new HashMap<Long, Optional<Split>>();
    oldestFirst.forEach(row -> splits.put(row.id(), split(row.name())));
    var spellings = spellings(oldestFirst, splits);

    var targets = new LinkedHashMap<Long, Target>();
    var titles = new HashMap<Long, String>();
    for (var row : oldestFirst) {
      var split = splits.get(row.id());
      if (row.ownerIsPeopleLead() && row.ownerUserId() != null) {
        targets.put(row.id(), new Target(null, row.ownerUserId()));
        titles.put(row.id(), split.map(Split::title).orElse(row.name()));
      } else if (split.isPresent()) {
        targets.put(row.id(), new Target(spellings.get(key(split.get().prefix())), null));
        titles.put(row.id(), split.get().title());
      } else {
        targets.put(row.id(), Target.NONE);
        titles.put(row.id(), row.name());
      }
    }

    var taken = new HashMap<Target, HashSet<String>>();
    var placements = new ArrayList<Placement>();
    for (var row : oldestFirst) {
      var target = targets.get(row.id());
      var inGroup = taken.computeIfAbsent(target, ignored -> new HashSet<>());
      var title = titles.get(row.id());
      if (!inGroup.add(key(title))) {
        title = row.name();
        if (!inGroup.add(key(title))) {
          title = row.name() + " (" + row.id() + ")";
          inGroup.add(key(title));
        }
      }
      placements.add(new Placement(row.id(), title, target));
    }

    var groupNames = targets.values().stream()
        .filter(target -> target.groupName() != null)
        .map(Target::groupName)
        .distinct()
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .toList();
    var personalOwners = targets.values().stream()
        .filter(target -> target.ownerUserId() != null)
        .map(Target::ownerUserId)
        .distinct()
        .sorted()
        .toList();
    placements.sort(comparing(Placement::id));
    return new Plan(groupNames, personalOwners, placements);
  }

  /** The spelling of each group, by its name in lower case: the most frequent, on a tie the oldest report's. */
  private static Map<String, String> spellings(List<Row> oldestFirst, Map<Long, Optional<Split>> splits) {
    var variants = new LinkedHashMap<String, LinkedHashMap<String, Integer>>();
    for (var row : oldestFirst) {
      if (row.ownerIsPeopleLead() && row.ownerUserId() != null) {
        continue;
      }
      splits.get(row.id()).ifPresent(split -> variants
          .computeIfAbsent(key(split.prefix()), ignored -> new LinkedHashMap<>())
          .merge(split.prefix(), 1, Integer::sum));
    }
    return variants.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
      // insertion order is the order of the oldest report of each spelling, and max keeps the first of equals
      String chosen = null;
      int most = 0;
      for (var variant : entry.getValue().entrySet()) {
        if (variant.getValue() > most) {
          chosen = variant.getKey();
          most = variant.getValue();
        }
      }
      return chosen;
    }));
  }

  private static Optional<Split> split(String name) {
    if (name == null) {
      return Optional.empty();
    }
    var colon = name.indexOf(':');
    if (colon < 0) {
      return Optional.empty();
    }
    var prefix = name.substring(0, colon).strip();
    var title = name.substring(colon + 1).strip();
    return prefix.isEmpty() || title.isEmpty() ? Optional.empty() : Optional.of(new Split(prefix, title));
  }

  private static String key(String text) {
    return text.toLowerCase(Locale.ROOT);
  }

  /** The placements by the group they go into, for counting. */
  public static Map<Target, Long> countByTarget(Plan plan) {
    return plan.placements().stream().collect(Collectors.groupingBy(Placement::target, Collectors.counting()));
  }
}
