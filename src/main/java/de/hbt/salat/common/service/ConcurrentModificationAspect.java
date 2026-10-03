package de.hbt.salat.common.service;

import static de.hbt.salat.common.exception.ErrorCode.BU_EMPLOYEE_COST_OVERLAP;
import static de.hbt.salat.common.exception.ErrorCode.CO_SIGN_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.EM_SIGN_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;
import static de.hbt.salat.common.exception.ErrorCode.XX_DUPLICATE_KEY;
import static org.hibernate.exception.ConstraintViolationException.ConstraintKind.UNIQUE;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;

/**
 * Macht aus einem Konflikt der Versionsnummer ({@code @Version} in
 * {@link de.hbt.salat.common.domain.AuditedEntity}) eine {@link BusinessRuleException}
 * {@code XX-0003} (#1237, ADR-0033). Zwei gleichzeitige Anfragen, meist ein doppelter Klick, lesen
 * denselben Stand; die zweite scheitert beim Schreiben, gespeichert hat die erste. Das ist ein
 * fachlicher Befund und keine Fehlerseite: der Controller fängt ihn wie jede andere
 * {@code BusinessRuleException} und muss von der Persistenz nichts wissen.
 *
 * <p>Der Aspekt liegt <b>außen</b> um die Transaktion: sie hat die Standardreihenfolge
 * {@link Ordered#LOWEST_PRECEDENCE}, der Aspekt {@link #ORDER}. Eine geänderte Entität schreibt
 * Hibernate beim Flush, und der kommt meist erst beim Commit, wenn die Methode schon zurückgekehrt
 * ist — ein {@code catch} im Service sähe den Konflikt nie. Ganz nach vorn ({@code HIGHEST_PRECEDENCE})
 * darf er nicht: dort liefe er noch vor dem {@code ExposeInvocationInterceptor}
 * ({@code HIGHEST_PRECEDENCE + 1}), ohne den ein AspectJ-Advice nicht ausgeführt werden kann.
 *
 * <p>Ruft ein Service einen anderen, übersetzt der innere, falls der Flush in ihm geschieht; die
 * {@code BusinessRuleException} rollt die gemeinsame Transaktion dann ebenso zurück.
 *
 * <p>Aus demselben Grund übersetzt der Aspekt auch die Verletzung eines Unique-Schlüssels (#1208):
 * zwei Anlagen mit demselben Kürzel, die beide an der Prüfung im Formular vorbeikamen, scheitern erst
 * am Schlüssel der Datenbank. Wo der Schlüssel einen eigenen Befund hat, meldet der Aspekt ihn, sonst
 * {@code XX-0004}. Jede andere Integritätsverletzung bleibt, was sie ist.
 */
@Slf4j
@Aspect
@Component
@Order(ConcurrentModificationAspect.ORDER)
public class ConcurrentModificationAspect {

  /** Hinter dem {@code ExposeInvocationInterceptor}, vor der Transaktion. */
  public static final int ORDER = 0;

  /**
   * Die Schlüssel mit eigenem Befund, nach ihrem Namen im Changelog. Verglichen wird per
   * {@code contains}, weil jede Datenbank den Namen anders einpackt: MySQL meldet
   * {@code employee.uk_employee_sign}, H2 den Index samt Schema und Suffix.
   */
  private static final Map<String, ErrorCode> FINDINGS_BY_UNIQUE_KEY = Map.of(
      "uk_employee_sign", EM_SIGN_TAKEN,
      "uk_customerorder_sign", CO_SIGN_TAKEN,
      "uk_employee_cost_name_valid_from", BU_EMPLOYEE_COST_OVERLAP);

  @AfterThrowing(pointcut = "within(de.hbt.salat..*) && @within(org.springframework.stereotype.Service)",
      throwing = "conflict")
  public void translate(JoinPoint joinPoint, OptimisticLockingFailureException conflict) {
    log.info("concurrent modification in {}: {}", joinPoint.getSignature().toShortString(), conflict.getMessage());
    throw new BusinessRuleException(XX_CONCURRENT_MODIFICATION, conflict);
  }

  /**
   * Nimmt jede {@link RuntimeException}, weil die Verletzung je nach Weg anders ankommt: über ein
   * Repository oder den Commit übersetzt Spring sie in eine {@link DataIntegrityViolationException}, ein
   * Flush direkt am {@code EntityManager} reicht Hibernates {@link ConstraintViolationException} durch.
   * Ein schon übersetzter Befund eines inneren Service bleibt, wie er ist.
   */
  @AfterThrowing(pointcut = "within(de.hbt.salat..*) && @within(org.springframework.stereotype.Service)",
      throwing = "violation")
  public void translateUniqueKeyViolation(JoinPoint joinPoint, RuntimeException violation) {
    if (violation instanceof ErrorCodeException) {
      return;
    }
    var uniqueKey = violatedUniqueKey(violation);
    if (uniqueKey.isEmpty()) {
      return;
    }
    log.info("unique key violated in {}: {}", joinPoint.getSignature().toShortString(), uniqueKey.get());
    throw new BusinessRuleException(findingFor(uniqueKey.get()), violation);
  }

  private static ErrorCode findingFor(String uniqueKey) {
    return FINDINGS_BY_UNIQUE_KEY.entrySet().stream()
        .filter(entry -> uniqueKey.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(XX_DUPLICATE_KEY);
  }

  /** Der Name des verletzten Unique-Schlüssels in Kleinbuchstaben, leer bei jeder anderen Verletzung. */
  private static Optional<String> violatedUniqueKey(RuntimeException violation) {
    for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException constraintViolation && constraintViolation.getKind() == UNIQUE) {
        return Optional.of(nameOf(constraintViolation.getConstraintName()));
      }
    }
    return violation instanceof DuplicateKeyException ? Optional.of("") : Optional.empty();
  }

  private static String nameOf(String constraintName) {
    return constraintName == null ? "" : constraintName.toLowerCase(Locale.ROOT);
  }
}
