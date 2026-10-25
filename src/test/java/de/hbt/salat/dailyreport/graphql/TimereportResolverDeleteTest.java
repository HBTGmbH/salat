package de.hbt.salat.dailyreport.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;

/**
 * Deleting over GraphQL (#1532). Here and not in {@code GraphqlIntegrationTest}: the soft delete of
 * {@code Timereport} is plain SQL on {@code timereport}, and the test database knows the table only as
 * {@code "Timereport"}. The rule of the period is the service's, tested there.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportResolverDeleteTest {

  @Mock
  private TimereportService timereportService;
  @Mock
  private AuthorizedEmployee authorizedEmployee;
  @InjectMocks
  private TimereportResolver resolver;

  @Test
  void a_readable_booking_is_deleted_through_the_service() {
    when(timereportService.getTimereportById(7L)).thenReturn(TimereportDTO.builder().id(7L).build());

    var payload = resolver.deleteTimereport(7L);

    verify(timereportService).deleteTimereportById(7L);
    assertThat(payload.id()).isEqualTo(7L);
  }

  /** Not found and not readable look the same (concept 6.3): the service answers both with {@code null}. */
  @Test
  void a_booking_the_user_cannot_see_is_not_found_and_not_deleted() {
    assertThatThrownBy(() -> resolver.deleteTimereport(7L))
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_TIME_REPORT_NOT_FOUND));
    verify(timereportService, never()).deleteTimereportById(anyLong());
  }
}
