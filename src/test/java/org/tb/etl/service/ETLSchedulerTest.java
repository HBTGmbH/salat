package org.tb.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.common.exception.ErrorCode.ETL_RUN_ALREADY_RUNNING;
import static org.tb.common.exception.ErrorCode.ETL_RUN_NOT_RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Trigger.SCHEDULED;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.util.DateUtils;

/**
 * Der nächtliche ETL-Lauf (#1071).
 *
 * <p>Er ist der einzige Weg in einen Lauf, hinter dem niemand sitzt. Was ein von Hand angestoßener
 * Lauf als Meldung auf der Seite beantwortet bekäme — „es läuft schon einer" —, muss hier in die
 * Liste, sonst stünde der Ausfall allein im Log und die Liste zeigte eine Lücke, die von einer
 * abgeschalteten Anwendung nicht zu unterscheiden wäre.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLSchedulerTest {

  @Mock
  private ETLService etlService;

  @Mock
  private ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Mock
  private AuthorizedUser authorizedUser;

  @InjectMocks
  private ETLScheduler etlScheduler;

  @Test
  void the_nightly_run_works_on_the_last_three_months_as_the_system() {
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);

    etlScheduler.runDaily();

    // Ohne HTTP-Anfrage gibt es keinen SecurityContext; ohne initForJob käme der Lauf an der
    // Rechteprüfung der Definitionen nicht vorbei (→ ADR-0006).
    verify(authorizedUser).initForJob();
    verify(etlService).executeAll(eq(lastThreeMonths()), eq(SCHEDULED));
    verify(etlService, never()).recordSkippedRun(any(), any(), anyString());
  }

  @Test
  void a_nightly_run_that_finds_another_one_running_leaves_a_skipped_entry_instead_of_failing() {
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
    doThrow(new BusinessRuleException(ETL_RUN_ALREADY_RUNNING, "24.09.2026 14:03:11"))
        .when(etlService).executeAll(any(), eq(SCHEDULED));

    // Kein Weiterwerfen: der geplante Job ist niemandes Anfrage, und ein Ausfall aus einem
    // erwarteten Grund ist kein Fehler des Jobs.
    assertThatCode(() -> etlScheduler.runDaily()).doesNotThrowAnyException();

    var range = ArgumentCaptor.forClass(LocalDateRange.class);
    var message = ArgumentCaptor.forClass(String.class);
    verify(etlService).recordSkippedRun(range.capture(), eq(SCHEDULED), message.capture());
    assertThat(range.getValue()).isEqualTo(lastThreeMonths());
    assertThat(message.getValue()).contains("bereits ein Lauf");
  }

  @Test
  void any_other_business_rule_still_ends_the_nightly_run_loudly() {
    // Nur die eine Absage ist vorgesehen. Jede andere Geschäftsregel bliebe sonst unbemerkt, weil
    // der Job keinen Adressaten hat, der nachfragt.
    when(authorizedUserProvider.getObject()).thenReturn(authorizedUser);
    doThrow(new BusinessRuleException(ETL_RUN_NOT_RUNNING))
        .when(etlService).executeAll(any(), eq(SCHEDULED));

    assertThatThrownBy(() -> etlScheduler.runDaily())
        .isInstanceOf(BusinessRuleException.class);

    verify(etlService, never()).recordSkippedRun(any(), any(), anyString());
  }

  private static LocalDateRange lastThreeMonths() {
    var today = DateUtils.today();
    return new LocalDateRange(today.minusMonths(3), today);
  }

}
