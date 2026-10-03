package de.hbt.salat.auth.service;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;
import static de.hbt.salat.auth.service.AuthService.ANY_MATCH;
import static de.hbt.salat.auth.service.AuthService.UNRESOLVED_PREFIX;
import static de.hbt.salat.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.AR_ACCESS_LEVEL_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.AR_CATEGORY_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.AR_GRANTEE_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.AR_GRANTEE_UNKNOWN;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_TOO_LONG;
import static de.hbt.salat.common.exception.ErrorCode.AR_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.AR_OBJECT_MALFORMED;
import static de.hbt.salat.common.exception.ErrorCode.AR_OBJECT_UNRESOLVED;
import static de.hbt.salat.common.exception.ErrorCode.AR_VALIDITY_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.AR_VALUE_TOO_LONG;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.AuthorizationGranteeProvider;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.auth.domain.AuthorizationRule;
import de.hbt.salat.auth.domain.AuthorizationRuleData;
import de.hbt.salat.auth.domain.AuthorizationRuleInfo;
import de.hbt.salat.auth.domain.AuthorizationRuleValue;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.ObjectJudgement;
import de.hbt.salat.auth.persistence.AuthorizationRuleRepository;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;

/**
 * Maintains the fine-grained authorization rules (#1074) — the rows that used to be edited by hand via SQL.
 *
 * <p>This is a tool for granting rights: a rule of category {@code EMPLOYEE} with {@code LOGIN} lets somebody act in
 * another person's name, so whoever writes rules can grant that to themselves. Hence management only, on the
 * controller and here, and every change goes into the log next to the audit columns the entity carries anyway.
 *
 * <p>Every write clears the rule cache. Without that the rule sits in the database and stays without effect for up to
 * {@code salat.auth-service.cache-expiry} — precisely the confusion that maintaining rules by hand produces today.
 *
 * <p>Every rule carries a name that says what it is for (#1168) — required when saving, unique regardless of case. The
 * name is for people only; {@link AuthService} never reads it. Rules from before the name stay valid without one and
 * get it the next time they are saved.
 *
 * <p>Grantees and objects are stored by id (#1204) and shown by the record they name today. A value the move to ids
 * could not assign carries {@link AuthService#UNRESOLVED_PREFIX}; it is kept, never matches and is marked in the
 * editor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@Authorized(requiresManager = true)
public class AuthorizationRuleService {

  /** Grantees, objects and access levels each live in one {@code varchar(255)}, joined by commas. */
  private static final int COLUMN_LENGTH = 255;

  private final AuthorizationRuleRepository authorizationRuleRepository;
  private final List<AuthorizationObjectProvider> objectProviders;
  private final List<AuthorizationGranteeProvider> granteeProviders;
  private final AuthService authService;
  private final AuthorizedUser authorizedUser;

  /** Sorted by name; the rules without one come last, in the order the list had before names existed. */
  @Transactional(readOnly = true)
  public List<AuthorizationRuleInfo> getAll() {
    requireManager();
    var rules = StreamSupport.stream(authorizationRuleRepository.findAll().spliterator(), false).toList();
    return toInfos(rules).stream()
        .sorted(comparing(AuthorizationRuleInfo::name, nullsLast(CASE_INSENSITIVE_ORDER))
            .thenComparing(AuthorizationRuleInfo::category)
            .thenComparing(info -> String.join(",", info.granteeIds())))
        .toList();
  }

  @Transactional(readOnly = true)
  public AuthorizationRuleInfo getById(long id) {
    requireManager();
    return toInfos(List.of(load(id))).getFirst();
  }

  /** The categories offered in the editor, plus any category an existing rule uses that no module offers. */
  @Transactional(readOnly = true)
  public List<String> getCategories(String keepCategory) {
    requireManager();
    var categories = new LinkedHashSet<String>();
    objectProviders.stream().map(AuthorizationObjectProvider::category).sorted().forEach(categories::add);
    StreamSupport.stream(authorizationRuleRepository.findAll().spliterator(), false)
        .map(AuthorizationRule::getCategory)
        .forEach(categories::add);
    if (keepCategory != null && !keepCategory.isBlank()) {
      categories.add(keepCategory);
    }
    return List.copyOf(categories);
  }

  /**
   * The selectable objects of a category, plus whatever the rule already carries — empty where the category cannot be
   * enumerated and nothing is kept. Without the kept values an edit would silently drop one the module no longer lists
   * (a person since hidden) and write back what the browser happened to preselect instead.
   */
  @Transactional(readOnly = true)
  public List<AuthorizationRuleValue> getObjects(String category, List<String> keep) {
    requireManager();
    var provider = providerOf(category);
    var offered = provider.map(AuthorizationObjectProvider::objects).orElse(List.of());
    return withKept(offered, keep, ids -> provider.map(p -> p.describe(ids)).orElse(Map.of()), provider.isPresent());
  }

  /**
   * Whether the object field of a category is free text: where the module cannot enumerate its objects, people type
   * what they know and the module translates it ({@link AuthorizationObjectProvider#objectIdOf}). A category no module
   * offers keeps its values as typed.
   */
  @Transactional(readOnly = true)
  public boolean isTyped(String category) {
    requireManager();
    return providerOf(category).map(provider -> provider.objects().isEmpty()).orElse(true);
  }

  @Transactional(readOnly = true)
  public String getObjectHintKey(String category) {
    requireManager();
    return providerOf(category).map(AuthorizationObjectProvider::objectHintKey).orElse(null);
  }

  /**
   * The logins offered as grantees, in the order the owning module hands them over, plus whatever the rule already
   * carries. Hidden people are left out of the offer — the kept ones are added back, so hiding somebody never makes an
   * existing rule uneditable.
   */
  @Transactional(readOnly = true)
  public List<AuthorizationRuleValue> getGranteeCandidates(List<String> keep) {
    requireManager();
    return withKept(offeredGrantees(), keep, this::describeGrantees, true);
  }

  private List<AuthorizationObject> offeredGrantees() {
    var byId = new LinkedHashMap<String, AuthorizationObject>();
    granteeProviders.stream()
        .flatMap(provider -> provider.granteeCandidates().stream())
        .forEach(candidate -> byId.putIfAbsent(candidate.id(), candidate));
    return List.copyOf(byId.values());
  }

  /**
   * @return the object ids no module knows — saved anyway, but worth saying out loud
   */
  public List<String> create(AuthorizationRuleData data) {
    requireManager();
    data = resolved(data, Set.of());
    validate(data, null, Set.of());
    var rule = new AuthorizationRule();
    apply(data, rule);
    authorizationRuleRepository.save(rule);
    log.info("Authorization rule '{}' created by {}: category={} grantees={} objects={} levels={} validity={}..{}",
        rule.getName(), authorizedUser.getLoginSign(), data.category(), data.granteeIds(), data.objectIds(), data.accessLevels(),
        data.validFrom(), data.validUntil());
    authService.clearCache();
    return unknownObjects(data);
  }

  /**
   * @return the object ids no module knows — saved anyway, but worth saying out loud
   */
  public List<String> update(long id, AuthorizationRuleData data) {
    requireManager();
    var rule = load(id);
    data = resolved(data, rule.getObjectId());
    validate(data, id, rule.getGranteeId());
    apply(data, rule);
    authorizationRuleRepository.save(rule);
    log.info("Authorization rule {} '{}' changed by {}: category={} grantees={} objects={} levels={} validity={}..{}",
        id, rule.getName(), authorizedUser.getLoginSign(), data.category(), data.granteeIds(), data.objectIds(), data.accessLevels(),
        data.validFrom(), data.validUntil());
    authService.clearCache();
    return unknownObjects(data);
  }

  /**
   * Ends a rule as of today instead of removing it, so that it stays traceable who was allowed what and until when.
   * A rule that never took effect can still be deleted.
   */
  public void end(long id) {
    requireManager();
    var rule = load(id);
    rule.setValidUntil(DateUtils.today());
    authorizationRuleRepository.save(rule);
    log.info("Authorization rule {} '{}' ended by {} as of {}",
        id, rule.getName(), authorizedUser.getLoginSign(), DateUtils.today());
    authService.clearCache();
  }

  public void delete(long id) {
    requireManager();
    var rule = load(id);
    log.info("Authorization rule {} '{}' deleted by {}: category={} grantees={} objects={}",
        id, rule.getName(), authorizedUser.getLoginSign(), rule.getCategory(), rule.getGranteeId(), rule.getObjectId());
    authorizationRuleRepository.delete(rule);
    authService.clearCache();
  }

  /**
   * @param id the rule being changed, {@code null} for a new one — a rule keeping its own name is no conflict
   * @param storedGrantees what the rule carries already — kept as it is, even where nothing answers to it any more
   */
  private void validate(AuthorizationRuleData data, Long id, Set<String> storedGrantees) {
    validateName(data.name(), id);
    if (data.category() == null || data.category().isBlank()) {
      throw new InvalidDataException(AR_CATEGORY_REQUIRED);
    }
    if (cleaned(data.granteeIds()).isEmpty()) {
      // Unlike the object, an empty grantee is not a wildcard — such a rule would simply never fire.
      throw new InvalidDataException(AR_GRANTEE_REQUIRED);
    }
    if (data.accessLevels() == null || data.accessLevels().isEmpty()) {
      throw new InvalidDataException(AR_ACCESS_LEVEL_REQUIRED);
    }
    if (data.validFrom() != null && data.validUntil() != null && data.validUntil().isBefore(data.validFrom())) {
      throw new InvalidDataException(AR_VALIDITY_INVALID);
    }
    if (joined(data.granteeIds()).length() > COLUMN_LENGTH || joined(data.objectIds()).length() > COLUMN_LENGTH) {
      // The column would take the first 255 characters and drop the rest — a rule that looks complete and is not.
      throw new InvalidDataException(AR_VALUE_TOO_LONG);
    }
    validateGrantees(cleaned(data.granteeIds()), storedGrantees);
    judgeObjects(data).entrySet().stream()
        .filter(entry -> entry.getValue() == ObjectJudgement.MALFORMED)
        .findFirst()
        .ifPresent(entry -> {
          throw new InvalidDataException(AR_OBJECT_MALFORMED, entry.getKey());
        });
  }

  private void validateName(String name, Long id) {
    var trimmed = trimmed(name);
    if (trimmed == null) {
      throw new InvalidDataException(AR_NAME_REQUIRED);
    }
    if (trimmed.length() > COLUMN_LENGTH) {
      throw new InvalidDataException(AR_NAME_TOO_LONG);
    }
    var taken = authorizationRuleRepository.findAllByNameIgnoreCase(trimmed).stream()
        .anyMatch(other -> !other.getId().equals(id));
    if (taken) {
      throw new InvalidDataException(AR_NAME_TAKEN, trimmed);
    }
  }

  private List<String> unknownObjects(AuthorizationRuleData data) {
    return judgeObjects(data).entrySet().stream()
        .filter(entry -> entry.getValue() == ObjectJudgement.UNKNOWN)
        .map(Map.Entry::getKey)
        .toList();
  }

  /**
   * The bare {@code *} never reaches a provider: "applies to every object" is a statement of this module, not of the
   * one that owns the category.
   */
  private Map<String, ObjectJudgement> judgeObjects(AuthorizationRuleData data) {
    var provider = providerOf(data.category());
    if (provider.isEmpty()) {
      return Map.of();
    }
    return cleaned(data.objectIds()).stream()
        .filter(objectId -> !ANY_MATCH.equals(objectId) && !objectId.startsWith(UNRESOLVED_PREFIX))
        .collect(Collectors.toMap(objectId -> objectId, objectId -> provider.get().judge(objectId), (a, b) -> a));
  }

  private Optional<AuthorizationObjectProvider> providerOf(String category) {
    return objectProviders.stream().filter(provider -> provider.category().equals(category)).findFirst();
  }

  private void apply(AuthorizationRuleData data, AuthorizationRule rule) {
    rule.setName(trimmed(data.name()));
    rule.setCategory(data.category().trim());
    rule.setGranteeId(new LinkedHashSet<>(cleaned(data.granteeIds())));
    rule.setObjectId(new LinkedHashSet<>(cleaned(data.objectIds())));
    rule.setAccessLevels(new LinkedHashSet<>(data.accessLevels()));
    rule.setValidFrom(data.validFrom());
    rule.setValidUntil(data.validUntil());
  }

  /**
   * The rules as list and form show them. The records the rules name are looked up once per category and once for all
   * grantees, not once per rule.
   */
  private List<AuthorizationRuleInfo> toInfos(List<AuthorizationRule> rules) {
    var grantees = describeGrantees(rules.stream().flatMap(rule -> rule.getGranteeId().stream()).toList());
    var objectsByCategory = new HashMap<String, Map<String, AuthorizationObject>>();
    rules.stream().collect(Collectors.groupingBy(AuthorizationRule::getCategory)).forEach((category, ofCategory) ->
        providerOf(category).ifPresent(provider -> objectsByCategory.put(category,
            provider.describe(ofCategory.stream().flatMap(rule -> rule.getObjectId().stream()).toList()))));
    return rules.stream().map(rule -> new AuthorizationRuleInfo(
        rule.getId(),
        rule.getName(),
        rule.getCategory(),
        providerOf(rule.getCategory()).map(AuthorizationObjectProvider::labelKey).orElse(null),
        List.copyOf(rule.getGranteeId()),
        List.copyOf(rule.getObjectId()),
        valuesOf(rule.getGranteeId(), grantees, true),
        valuesOf(rule.getObjectId(), objectsByCategory.getOrDefault(rule.getCategory(), Map.of()),
            objectsByCategory.containsKey(rule.getCategory())),
        List.copyOf(rule.getAccessLevels()),
        rule.getValidFrom(),
        rule.getValidUntil()
    )).toList();
  }

  /** Sorted by what is shown, the wildcard first. */
  private static List<AuthorizationRuleValue> valuesOf(Collection<String> ids, Map<String, AuthorizationObject> described,
                                                       boolean resolvable) {
    return ids.stream()
        .map(id -> valueOf(id, described, resolvable))
        .sorted(comparing((AuthorizationRuleValue value) -> !ANY_MATCH.equals(value.id()))
            .thenComparing(value -> value.shown() == null ? "" : value.shown(), CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /**
   * @param resolvable whether a module answers for these values at all — a category no module offers keeps its values
   *     as they are stored, and they are not marked
   */
  private static AuthorizationRuleValue valueOf(String id, Map<String, AuthorizationObject> described,
                                                boolean resolvable) {
    if (ANY_MATCH.equals(id)) {
      return new AuthorizationRuleValue(id, null, null, false);
    }
    if (id.startsWith(UNRESOLVED_PREFIX)) {
      return new AuthorizationRuleValue(id, id.substring(UNRESOLVED_PREFIX.length()), null, true);
    }
    var object = described.get(id);
    if (object != null) {
      return AuthorizationRuleValue.of(object);
    }
    return new AuthorizationRuleValue(id, id, null, resolvable);
  }

  /** The offered entries, then what is kept and not offered — named by its record, or marked where none answers. */
  private static List<AuthorizationRuleValue> withKept(List<AuthorizationObject> offered, List<String> keep,
                                                       Function<Collection<String>, Map<String, AuthorizationObject>> describe,
                                                       boolean resolvable) {
    var values = new ArrayList<AuthorizationRuleValue>();
    offered.forEach(object -> values.add(AuthorizationRuleValue.of(object)));
    Set<String> known = offered.stream().map(AuthorizationObject::id).collect(Collectors.toSet());
    var kept = (keep == null ? List.<String>of() : keep).stream()
        .filter(id -> id != null && !id.isBlank() && !ANY_MATCH.equals(id) && !known.contains(id))
        .distinct()
        .toList();
    if (!kept.isEmpty()) {
      var described = describe.apply(kept);
      kept.forEach(id -> values.add(valueOf(id, described, resolvable)));
    }
    return values;
  }

  private Map<String, AuthorizationObject> describeGrantees(Collection<String> granteeIds) {
    var ids = granteeIds.stream()
        .filter(id -> !ANY_MATCH.equals(id) && !id.startsWith(UNRESOLVED_PREFIX))
        .collect(Collectors.toSet());
    var described = new HashMap<String, AuthorizationObject>();
    if (!ids.isEmpty()) {
      granteeProviders.forEach(provider -> provider.describe(ids).forEach(described::putIfAbsent));
    }
    return described;
  }

  /**
   * A grantee is the wildcard or a login some module knows (#1204). What the rule carries already stays, even where
   * nothing answers to it any more: an edit of a rule must not fail on a value nobody touched.
   */
  private void validateGrantees(List<String> granteeIds, Set<String> stored) {
    var toCheck = granteeIds.stream()
        .filter(id -> !ANY_MATCH.equals(id) && !stored.contains(id))
        .toList();
    var known = describeGrantees(toCheck).keySet();
    toCheck.stream().filter(id -> !known.contains(id)).findFirst().ifPresent(id -> {
      throw new InvalidDataException(AR_GRANTEE_UNKNOWN, id);
    });
  }

  /**
   * Translates the objects typed into a free-text field into the ids that are stored (#1204). Only for a category whose
   * module cannot enumerate its objects — elsewhere the values come from the list and are ids already. What the rule
   * carries already comes back from the form as stored and stays as it is.
   */
  private AuthorizationRuleData resolved(AuthorizationRuleData data, Set<String> stored) {
    var provider = providerOf(data.category() == null ? null : data.category().trim());
    if (provider.isEmpty() || !provider.get().objects().isEmpty()) {
      return data;
    }
    var objectIds = cleaned(data.objectIds()).stream()
        .map(value -> ANY_MATCH.equals(value) || value.startsWith(UNRESOLVED_PREFIX) || stored.contains(value)
            ? value
            : provider.get().objectIdOf(value).orElseThrow(() -> new InvalidDataException(AR_OBJECT_UNRESOLVED, value)))
        .toList();
    return new AuthorizationRuleData(data.name(), data.category(), data.granteeIds(), objectIds, data.accessLevels(),
        data.validFrom(), data.validUntil());
  }

  private AuthorizationRule load(long id) {
    return authorizationRuleRepository.findById(id).orElseThrow(() -> new InvalidDataException(AR_NOT_FOUND));
  }

  private static List<String> cleaned(List<String> values) {
    if (values == null) {
      return List.of();
    }
    // A comma would split one value into two on the way back out of the column.
    return values.stream()
        .filter(value -> value != null)
        .map(value -> value.replace(",", " ").trim())
        .filter(value -> !value.isEmpty())
        .distinct()
        .toList();
  }

  private static String trimmed(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String joined(List<String> values) {
    return String.join(",", cleaned(values));
  }

  private void requireManager() {
    if (!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
  }

}
