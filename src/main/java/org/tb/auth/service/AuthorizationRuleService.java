package org.tb.auth.service;

import static java.util.Comparator.comparing;
import static org.tb.auth.service.AuthService.ANY_MATCH;
import static org.tb.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static org.tb.common.exception.ErrorCode.AR_ACCESS_LEVEL_REQUIRED;
import static org.tb.common.exception.ErrorCode.AR_CATEGORY_REQUIRED;
import static org.tb.common.exception.ErrorCode.AR_GRANTEE_REQUIRED;
import static org.tb.common.exception.ErrorCode.AR_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.AR_OBJECT_MALFORMED;
import static org.tb.common.exception.ErrorCode.AR_VALIDITY_INVALID;
import static org.tb.common.exception.ErrorCode.AR_VALUE_TOO_LONG;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.AuthorizationGranteeProvider;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizationObjectProvider;
import org.tb.auth.domain.AuthorizationRule;
import org.tb.auth.domain.AuthorizationRuleData;
import org.tb.auth.domain.AuthorizationRuleInfo;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.ObjectJudgement;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;

/**
 * Maintains the fine-grained authorization rules (#1074) — the rows that used to be edited by hand via SQL.
 *
 * <p>This is a tool for granting rights: a rule of category {@code EMPLOYEE} with {@code LOGIN} lets somebody act in
 * another person's name, so whoever writes rules can grant that to themselves. Hence management only, on the
 * controller and here, and every change goes into the log next to the audit columns the entity carries anyway.
 *
 * <p>Every write clears the rule cache. Without that the rule sits in the database and stays without effect for up to
 * {@code salat.auth-service.cache-expiry} — precisely the confusion that maintaining rules by hand produces today.
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

  @Transactional(readOnly = true)
  public List<AuthorizationRuleInfo> getAll() {
    requireManager();
    return StreamSupport.stream(authorizationRuleRepository.findAll().spliterator(), false)
        .map(this::toInfo)
        .sorted(comparing(AuthorizationRuleInfo::category)
            .thenComparing(info -> String.join(",", info.granteeIds())))
        .toList();
  }

  @Transactional(readOnly = true)
  public AuthorizationRuleInfo getById(long id) {
    requireManager();
    return toInfo(load(id));
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

  /** The selectable objects of a category — empty where the category cannot be enumerated. */
  @Transactional(readOnly = true)
  public List<AuthorizationObject> getObjects(String category) {
    requireManager();
    return providerOf(category).map(AuthorizationObjectProvider::objects).orElse(List.of());
  }

  @Transactional(readOnly = true)
  public String getObjectHintKey(String category) {
    requireManager();
    return providerOf(category).map(AuthorizationObjectProvider::objectHintKey).orElse(null);
  }

  /**
   * The logins offered as grantees. Hidden people are left out — what a rule already carries is added back by the
   * editor, so hiding somebody never makes an existing rule uneditable.
   */
  @Transactional(readOnly = true)
  public List<String> getGranteeCandidates() {
    requireManager();
    return granteeProviders.stream()
        .flatMap(provider -> provider.granteeCandidates().stream())
        .distinct()
        .sorted()
        .toList();
  }

  /**
   * @return the object ids no module knows — saved anyway, but worth saying out loud
   */
  public List<String> create(AuthorizationRuleData data) {
    requireManager();
    validate(data);
    var rule = new AuthorizationRule();
    apply(data, rule);
    authorizationRuleRepository.save(rule);
    log.info("Authorization rule created by {}: category={} grantees={} objects={} levels={} validity={}..{}",
        authorizedUser.getLoginSign(), data.category(), data.granteeIds(), data.objectIds(), data.accessLevels(),
        data.validFrom(), data.validUntil());
    authService.clearCache();
    return unknownObjects(data);
  }

  /**
   * @return the object ids no module knows — saved anyway, but worth saying out loud
   */
  public List<String> update(long id, AuthorizationRuleData data) {
    requireManager();
    validate(data);
    var rule = load(id);
    apply(data, rule);
    authorizationRuleRepository.save(rule);
    log.info("Authorization rule {} changed by {}: category={} grantees={} objects={} levels={} validity={}..{}",
        id, authorizedUser.getLoginSign(), data.category(), data.granteeIds(), data.objectIds(), data.accessLevels(),
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
    log.info("Authorization rule {} ended by {} as of {}", id, authorizedUser.getLoginSign(), DateUtils.today());
    authService.clearCache();
  }

  public void delete(long id) {
    requireManager();
    var rule = load(id);
    log.info("Authorization rule {} deleted by {}: category={} grantees={} objects={}",
        id, authorizedUser.getLoginSign(), rule.getCategory(), rule.getGranteeId(), rule.getObjectId());
    authorizationRuleRepository.delete(rule);
    authService.clearCache();
  }

  private void validate(AuthorizationRuleData data) {
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
    judgeObjects(data).entrySet().stream()
        .filter(entry -> entry.getValue() == ObjectJudgement.MALFORMED)
        .findFirst()
        .ifPresent(entry -> {
          throw new InvalidDataException(AR_OBJECT_MALFORMED, entry.getKey());
        });
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
        .filter(objectId -> !ANY_MATCH.equals(objectId))
        .collect(Collectors.toMap(objectId -> objectId, objectId -> provider.get().judge(objectId), (a, b) -> a));
  }

  private Optional<AuthorizationObjectProvider> providerOf(String category) {
    return objectProviders.stream().filter(provider -> provider.category().equals(category)).findFirst();
  }

  private void apply(AuthorizationRuleData data, AuthorizationRule rule) {
    rule.setCategory(data.category().trim());
    rule.setGranteeId(new LinkedHashSet<>(cleaned(data.granteeIds())));
    rule.setObjectId(new LinkedHashSet<>(cleaned(data.objectIds())));
    rule.setAccessLevels(new LinkedHashSet<>(data.accessLevels()));
    rule.setValidFrom(data.validFrom());
    rule.setValidUntil(data.validUntil());
  }

  private AuthorizationRuleInfo toInfo(AuthorizationRule rule) {
    return new AuthorizationRuleInfo(
        rule.getId(),
        rule.getCategory(),
        providerOf(rule.getCategory()).map(AuthorizationObjectProvider::labelKey).orElse(null),
        List.copyOf(rule.getGranteeId()),
        List.copyOf(rule.getObjectId()),
        List.copyOf(rule.getAccessLevels()),
        rule.getValidFrom(),
        rule.getValidUntil()
    );
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

  private static String joined(List<String> values) {
    return String.join(",", cleaned(values));
  }

  private void requireManager() {
    if (!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
  }

}
