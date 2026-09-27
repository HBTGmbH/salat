package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.palette.PaletteKind.PERSON;
import static org.tb.common.palette.PaletteKind.SUBORDER;
import static org.tb.dailyreport.controller.DailyReportUiStateKeyContributor.EMPLOYEE_CONTRACT_ID;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteKind;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;
import org.tb.common.test.FixedClock;
import org.tb.common.web.UiState;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.EmployeeorderService;

/**
 * The booking targets the command palette adds to objects other modules found (#1157). "Buchen auf"
 * is offered for a suborder only where the booking form would accept the booking today, for the
 * contract the form would open with: the remembered selection if the user may read it, otherwise
 * their own current contract. That contract needs an employee order on the suborder valid today,
 * and today has to be open for writing on it — a people lead reads a team member's contract but may
 * not book on its open days, a released period is closed for its owner. A person gets the daily and
 * the matrix view where the user may read the contract.
 *
 * <p>Readability and writability are decided by {@link EmployeecontractService} and
 * {@link TimereportAuthorization}; here they answer the way they do for the four roles.
 */
@ExtendWith(MockitoExtension.class)
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyReportPaletteProviderTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final long OWN_EMPLOYEE_ID = 5L;
  private static final long OWN_CONTRACT_ID = 42L;
  private static final long OTHER_CONTRACT_ID = 43L;
  private static final long MAINTENANCE_ID = 501L;
  private static final long DEVELOPMENT_ID = 502L;

  @Mock
  private UiState uiState;
  @Mock
  private AuthorizedEmployee authorizedEmployee;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private EmployeeorderService employeeorderService;
  @Mock
  private TimereportAuthorization timereportAuthorization;

  @InjectMocks
  private DailyReportPaletteProvider provider;

  private final Employeecontract ownContract = contract(OWN_CONTRACT_ID);
  private final Employeecontract otherContract = contract(OTHER_CONTRACT_ID);

  @ParameterizedTest
  @EnumSource(value = PaletteKind.class, names = {"CUSTOMERORDER", "CUSTOMER"})
  void answers_nothing_for_orders_and_customers(PaletteKind kind) {
    var targets = provider.targetsFor(kind, List.of(new PaletteHit(kind, "MUSTER-01", "MUSTER-01", null, null,
        false, false, 4, List.of())));

    assertThat(targets).isEmpty();
    verifyNoInteractions(uiState, authorizedEmployee, employeecontractService, employeeorderService,
        timereportAuthorization);
  }

  // --- Buchen auf: the four roles ---------------------------------------------------------------

  @Test
  void offers_an_employee_booking_on_a_suborder_of_their_own_contract() {
    ownCurrentContract();
    when(timereportAuthorization.isWriteAllowedOn(ownContract, TODAY)).thenReturn(true);
    when(employeeorderService.getBookableSuborderIds(OWN_CONTRACT_ID, List.of(MAINTENANCE_ID), TODAY))
        .thenReturn(Set.of(MAINTENANCE_ID));

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets).containsOnlyKeys("501");
    assertThat(targets.get("501")).containsExactly(new PaletteTarget(
        PaletteText.of("main.palette.target.suborder.book", "MUSTER-01/03"),
        "/dailyreport/timereports/new?suborderId=501&employeecontractId=42", 5));
  }

  /** The form opens for the selected contract, so the link names it, not the manager's own one. */
  @Test
  void offers_a_manager_booking_for_the_selected_contract_of_somebody_else() {
    selectedContract(OTHER_CONTRACT_ID);
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID)).thenReturn(otherContract);
    when(timereportAuthorization.isWriteAllowedOn(otherContract, TODAY)).thenReturn(true);
    when(employeeorderService.getBookableSuborderIds(OTHER_CONTRACT_ID, List.of(MAINTENANCE_ID), TODAY))
        .thenReturn(Set.of(MAINTENANCE_ID));

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets.get("501")).extracting(PaletteTarget::href)
        .containsExactly("/dailyreport/timereports/new?suborderId=501&employeecontractId=43");
    verifyNoInteractions(authorizedEmployee);
  }

  /** A people lead reads the team member's contract, but an open day is the member's own to book. */
  @Test
  void offers_a_people_lead_no_booking_on_an_open_day_of_a_team_member() {
    selectedContract(OTHER_CONTRACT_ID);
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID)).thenReturn(otherContract);
    when(timereportAuthorization.isWriteAllowedOn(otherContract, TODAY)).thenReturn(false);

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets).isEmpty();
    verifyNoInteractions(employeeorderService);
  }

  @Test
  void offers_a_restricted_user_booking_on_their_own_selected_contract() {
    selectedContract(OWN_CONTRACT_ID);
    when(employeecontractService.getEmployeecontractForView(OWN_CONTRACT_ID)).thenReturn(ownContract);
    when(timereportAuthorization.isWriteAllowedOn(ownContract, TODAY)).thenReturn(true);
    when(employeeorderService.getBookableSuborderIds(OWN_CONTRACT_ID, List.of(MAINTENANCE_ID), TODAY))
        .thenReturn(Set.of(MAINTENANCE_ID));

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets.get("501")).extracting(PaletteTarget::href)
        .containsExactly("/dailyreport/timereports/new?suborderId=501&employeecontractId=42");
    verifyNoInteractions(authorizedEmployee);
  }

  // --- Buchen auf: suborder and day -------------------------------------------------------------

  @Test
  void offers_no_booking_on_a_suborder_without_an_employee_order_valid_today() {
    ownCurrentContract();
    when(timereportAuthorization.isWriteAllowedOn(ownContract, TODAY)).thenReturn(true);
    when(employeeorderService.getBookableSuborderIds(OWN_CONTRACT_ID, List.of(MAINTENANCE_ID, DEVELOPMENT_ID), TODAY))
        .thenReturn(Set.of(DEVELOPMENT_ID));

    var targets = provider.targetsFor(SUBORDER, List.of(
        suborder(MAINTENANCE_ID, "MUSTER-01/03"), suborder(DEVELOPMENT_ID, "MUSTER-01/04")));

    assertThat(targets).containsOnlyKeys("502");
    assertThat(targets.get("502")).extracting(PaletteTarget::label)
        .containsExactly(PaletteText.of("main.palette.target.suborder.book", "MUSTER-01/04"));
  }

  @Test
  void offers_no_booking_where_today_is_released_already() {
    ownCurrentContract();
    when(timereportAuthorization.isWriteAllowedOn(ownContract, TODAY)).thenReturn(false);

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets).isEmpty();
    verifyNoInteractions(employeeorderService);
  }

  // --- Buchen auf: which contract ---------------------------------------------------------------

  /** A remembered foreign contract is not read; the form would fall back the same way. */
  @Test
  void falls_back_to_the_own_contract_where_the_selected_one_is_not_readable() {
    selectedContract(OTHER_CONTRACT_ID);
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID))
        .thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));
    ownCurrentContract();
    when(timereportAuthorization.isWriteAllowedOn(ownContract, TODAY)).thenReturn(true);
    when(employeeorderService.getBookableSuborderIds(OWN_CONTRACT_ID, List.of(MAINTENANCE_ID), TODAY))
        .thenReturn(Set.of(MAINTENANCE_ID));

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets.get("501")).extracting(PaletteTarget::href)
        .containsExactly("/dailyreport/timereports/new?suborderId=501&employeecontractId=42");
  }

  @Test
  void falls_back_to_the_own_contract_where_the_selected_one_does_not_exist() {
    selectedContract(OTHER_CONTRACT_ID);
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID)).thenReturn(null);
    ownCurrentContract();
    when(timereportAuthorization.isWriteAllowedOn(ownContract, TODAY)).thenReturn(true);
    when(employeeorderService.getBookableSuborderIds(OWN_CONTRACT_ID, List.of(MAINTENANCE_ID), TODAY))
        .thenReturn(Set.of(MAINTENANCE_ID));

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets.get("501")).extracting(PaletteTarget::href)
        .containsExactly("/dailyreport/timereports/new?suborderId=501&employeecontractId=42");
  }

  /** An admin is no employee and has no contract to book on. */
  @Test
  void offers_no_booking_without_an_employee() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(null);

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets).isEmpty();
    verifyNoInteractions(timereportAuthorization, employeeorderService);
  }

  @Test
  void offers_no_booking_without_a_current_or_coming_contract() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(OWN_EMPLOYEE_ID);
    when(employeecontractService.getCurrentContract(OWN_EMPLOYEE_ID)).thenReturn(Optional.empty());

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets).isEmpty();
    verifyNoInteractions(timereportAuthorization, employeeorderService);
  }

  /** No running contract and more than one to come: the form would not know which one either. */
  @Test
  void offers_no_booking_where_the_current_contract_is_ambiguous() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(OWN_EMPLOYEE_ID);
    when(employeecontractService.getCurrentContract(OWN_EMPLOYEE_ID))
        .thenThrow(new IncorrectResultSizeDataAccessException(1, 2));

    var targets = provider.targetsFor(SUBORDER, List.of(suborder(MAINTENANCE_ID, "MUSTER-01/03")));

    assertThat(targets).isEmpty();
    verifyNoInteractions(timereportAuthorization, employeeorderService);
  }

  // --- Einzel- und Matrixübersicht: the four roles ----------------------------------------------

  @Test
  void offers_a_manager_the_views_of_every_person() {
    when(employeecontractService.getEmployeecontractForView(OWN_CONTRACT_ID)).thenReturn(ownContract);
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID)).thenReturn(otherContract);

    var targets = provider.targetsFor(PERSON, List.of(person(OWN_CONTRACT_ID), person(OTHER_CONTRACT_ID)));

    assertThat(targets).containsOnlyKeys("42", "43");
    assertThat(targets.get("43")).containsExactly(
        new PaletteTarget(PaletteText.of("main.palette.target.person.daily"),
            "/dailyreport/daily?fEmployeeContractId=43", 10),
        new PaletteTarget(PaletteText.of("main.palette.target.person.matrix"),
            "/dailyreport/matrix?fEmployeeContractId=43", 11));
  }

  @Test
  void offers_a_people_lead_the_views_of_a_team_member_only() {
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID)).thenReturn(otherContract);
    when(employeecontractService.getEmployeecontractForView(44L))
        .thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));

    var targets = provider.targetsFor(PERSON, List.of(person(OTHER_CONTRACT_ID), person(44L)));

    assertThat(targets).containsOnlyKeys("43");
  }

  @Test
  void offers_an_employee_their_own_views_but_none_of_a_colleague() {
    when(employeecontractService.getEmployeecontractForView(OWN_CONTRACT_ID)).thenReturn(ownContract);
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID))
        .thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));

    var targets = provider.targetsFor(PERSON, List.of(person(OWN_CONTRACT_ID), person(OTHER_CONTRACT_ID)));

    assertThat(targets).containsOnlyKeys("42");
    assertThat(targets.get("42")).extracting(PaletteTarget::href).containsExactly(
        "/dailyreport/daily?fEmployeeContractId=42", "/dailyreport/matrix?fEmployeeContractId=42");
  }

  @Test
  void offers_a_restricted_user_no_view_of_somebody_else() {
    when(employeecontractService.getEmployeecontractForView(OTHER_CONTRACT_ID))
        .thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));

    var targets = provider.targetsFor(PERSON, List.of(person(OTHER_CONTRACT_ID)));

    assertThat(targets).isEmpty();
  }

  private void selectedContract(long contractId) {
    when(uiState.getLongValue(EMPLOYEE_CONTRACT_ID)).thenReturn(contractId);
  }

  private void ownCurrentContract() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(OWN_EMPLOYEE_ID);
    when(employeecontractService.getCurrentContract(OWN_EMPLOYEE_ID)).thenReturn(Optional.of(ownContract));
  }

  private static PaletteHit suborder(long id, String completeSign) {
    return new PaletteHit(SUBORDER, String.valueOf(id), completeSign, "Wartung", null, false, false, 4, List.of());
  }

  private static PaletteHit person(long contractId) {
    return new PaletteHit(PERSON, String.valueOf(contractId), "Person P", "ppp", null, false, false, 4, List.of());
  }

  private static Employeecontract contract(long id) {
    var contract = new Employeecontract();
    setField(contract, "id", id);
    return contract;
  }
}
