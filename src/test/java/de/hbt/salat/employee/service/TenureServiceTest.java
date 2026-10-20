package de.hbt.salat.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeeEntryDate;
import de.hbt.salat.employee.domain.EmploymentSpan;
import de.hbt.salat.employee.persistence.EmployeeEntryRepository;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Who may read whose tenure, and where the entry date comes in (#1501). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TenureServiceTest {

  private static final long ANNA = 1L;
  private static final long BERT = 2L;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

  private final EmployeecontractRepository contracts = mock(EmployeecontractRepository.class);
  private final EmployeeEntryRepository entries = mock(EmployeeEntryRepository.class);
  private final AuthorizedUser authorizedUser = mock(AuthorizedUser.class);
  private final AuthorizedEmployee authorizedEmployee = mock(AuthorizedEmployee.class);
  private final TenureService service = new TenureService(contracts, entries, authorizedUser, authorizedEmployee);

  @BeforeEach
  void setUp() {
    when(contracts.findSpansByEmployeeId(BERT)).thenReturn(List.of(new EmploymentSpan(BERT, date("2007-01-01"), null)));
    when(entries.findEntryDateByEmployeeId(BERT)).thenReturn(Optional.of(date("2001-08-01")));
    when(authorizedEmployee.getEmployeeId()).thenReturn(ANNA);
  }

  @Test
  void a_person_reads_their_own_tenure() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(BERT);

    assertThat(service.getTenure(BERT).reachedOn(25)).contains(date("2026-08-01"));
  }

  @Test
  void a_person_does_not_read_the_tenure_of_somebody_else() {
    assertThatThrownBy(() -> service.getTenure(BERT)).isInstanceOf(AuthorizationException.class);
  }

  @Test
  void management_reads_every_tenure() {
    when(authorizedUser.isManager()).thenReturn(true);

    assertThat(service.getTenure(BERT).reachedOn(1)).contains(date("2002-08-01"));
  }

  @Test
  void the_job_gets_the_tenure_of_everybody_employed_with_their_entry_dates() {
    when(contracts.findSpansOfEmployeesEmployedOn(TODAY)).thenReturn(List.of(
        new EmploymentSpan(ANNA, date("2021-03-01"), date("2023-12-31")),
        new EmploymentSpan(ANNA, date("2024-01-01"), null),
        new EmploymentSpan(BERT, date("2007-01-01"), null)));
    when(entries.findAllEntryDates()).thenReturn(List.of(new EmployeeEntryDate(BERT, date("2001-08-01"))));

    var tenures = service.getTenuresOfEmployeesEmployedOn(TODAY);

    assertThat(tenures.get(ANNA).reachedOn(5)).contains(date("2026-03-01"));
    assertThat(tenures.get(BERT).reachedOn(25)).contains(date("2026-08-01"));
  }

  private static LocalDate date(String text) {
    return LocalDate.parse(text);
  }
}
