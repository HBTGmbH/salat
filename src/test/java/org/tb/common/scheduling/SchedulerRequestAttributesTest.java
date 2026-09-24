package org.tb.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;
import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.context.request.RequestScope;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.web.UiState;

/**
 * Was die Aufräumung eines Jobs zusagt (#1084).
 *
 * <p>Ein Job öffnet sich einen Request-Scope, setzt {@link AuthorizedUser} in den Job-Modus und
 * unbindet wieder (→ ADR-0006). Die Zusage dahinter ist eine einzige: <b>der Job-Modus darf nicht in
 * den nächsten Lauf durchschlagen.</b> Bis #1084 stand daneben ein
 * {@code destroyScopedBean("authorizedUser")}, das bei jedem Durchlauf warf und von einem
 * {@code catch} verschluckt wurde — die Zusage hielt also nie die Zeile, die danach aussah, sondern
 * immer schon das {@code resetRequestAttributes()} darunter. Dieser Test hält fest, dass sie
 * weiterhin gehalten wird, jetzt wo die Zeile weg ist.
 *
 * <p>Der Kontext ist absichtlich klein statt {@code @SpringBootTest}: geprüft wird das Zusammenspiel
 * von Scoped Proxy, {@link RequestScope} und {@link SchedulerRequestAttributes}, und das ist
 * vollständig, sobald die beiden request-scoped Bohnen registriert sind.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SchedulerRequestAttributesTest {

  @AfterEach
  void unbind() {
    resetRequestAttributes();
  }

  @Test
  void job_mode_does_not_carry_from_one_run_into_the_next() {
    withAuthorizedUser(authorizedUser -> {
      // erster Lauf: der Job meldet sich an und ist damit Geschäftsführung
      assertThat(runWithOwnScope(authorizedUser, true)).isTrue();

      // zweiter Lauf, ohne Anmeldung: eine durchgeschlagene Bohne trüge den Job-Modus mit und
      // antwortete wieder mit true - ohne SecurityContext ist die richtige Antwort false
      assertThat(runWithOwnScope(authorizedUser, false)).isFalse();

      // und ein dritter Lauf kann sich wieder anmelden - das Abräumen macht den Scope nicht kaputt
      assertThat(runWithOwnScope(authorizedUser, true)).isTrue();
    });
  }

  @Test
  void unbinding_leaves_no_scope_behind_at_all() {
    withAuthorizedUser(authorizedUser -> {
      runWithOwnScope(authorizedUser, true);

      // Das Aufräumen ist vollständig: nach dem Unbinden gibt es keinen Scope mehr, aus dem eine
      // Bohne des vorherigen Laufs noch zu holen wäre.
      assertThatThrownBy(authorizedUser::isManager)
          .isInstanceOf(ScopeNotActiveException.class)
          .hasMessageContaining("Scope 'request' is not active");
    });
  }

  /** Ein Lauf, wie ihn ein Scheduler fährt: Scope auf, ggf. anmelden, fragen, Scope zu. */
  private static boolean runWithOwnScope(AuthorizedUser authorizedUser, boolean initForJob) {
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      if (initForJob) {
        authorizedUser.initForJob();
      }
      return authorizedUser.isManager();
    } finally {
      resetRequestAttributes();
    }
  }

  /**
   * @param test bekommt den Scoped Proxy, nicht die Bohne - genau das, was ein Scheduler über
   *     seinen {@code ObjectProvider} in der Hand hält
   */
  private static void withAuthorizedUser(Consumer<AuthorizedUser> test) {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.getBeanFactory().registerScope(SCOPE_REQUEST, new RequestScope());
      context.register(UiState.class, AuthorizedUser.class);
      context.refresh();

      test.accept(context.getBean("authorizedUser", AuthorizedUser.class));
    }
  }

}
