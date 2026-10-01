package de.hbt.salat.common.service;

import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.exception.BusinessRuleException;

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
 */
@Slf4j
@Aspect
@Component
@Order(ConcurrentModificationAspect.ORDER)
public class ConcurrentModificationAspect {

  /** Hinter dem {@code ExposeInvocationInterceptor}, vor der Transaktion. */
  public static final int ORDER = 0;

  @AfterThrowing(pointcut = "within(de.hbt.salat..*) && @within(org.springframework.stereotype.Service)",
      throwing = "conflict")
  public void translate(JoinPoint joinPoint, OptimisticLockingFailureException conflict) {
    log.info("concurrent modification in {}: {}", joinPoint.getSignature().toShortString(), conflict.getMessage());
    throw new BusinessRuleException(XX_CONCURRENT_MODIFICATION, conflict);
  }
}
