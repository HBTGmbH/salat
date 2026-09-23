package org.tb.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.common.exception.AuthorizationException;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.persistence.ETLRunHistoryRepository;

/**
 * Wer die Laufhistorie sehen darf (#573): Geschäftsführung und Administration — das entscheidet
 * {@link ETLAuthorization} — und darüber hinaus jede Anmeldung mit einer Regel der Kategorie
 * {@code ETL} und {@code EXECUTE}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLRunHistoryServiceTest {

  @Mock
  private ETLRunHistoryRepository runHistoryRepository;

  @Mock
  private ETLAuthorization etlAuthorization;

  @InjectMocks
  private ETLRunHistoryService etlRunHistoryService;

  @Test
  void an_execute_rule_is_enough_to_see_the_runs() {
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(runHistoryRepository.findByOrderByStartedAtDesc(any())).thenReturn(List.of(new ETLRunHistory()));

    assertThat(etlRunHistoryService.getLatestRuns(100, false)).hasSize(1);
  }

  @Test
  void without_a_rule_the_list_is_refused_rather_than_empty() {
    // Leere Liste statt Absage waere eine Falschaussage: es gibt Laeufe, nur nicht fuer diese Augen.
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlRunHistoryService.getLatestRuns(100, false))
        .isInstanceOf(AuthorizationException.class);

    verify(runHistoryRepository, never()).findByOrderByStartedAtDesc(any());
  }

  @Test
  void the_filter_asks_for_everything_that_did_not_succeed() {
    when(etlAuthorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);

    etlRunHistoryService.getLatestRuns(25, true);

    verify(runHistoryRepository).findByStatusNotOrderByStartedAtDesc(eq(SUCCEEDED), any());
  }

}
