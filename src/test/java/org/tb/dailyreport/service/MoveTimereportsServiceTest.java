package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.common.exception.ErrorCode.TR_MOVE_DATE_RANGE_OUTSIDE_TARGET;
import static org.tb.common.exception.ErrorCode.TR_MOVE_SOURCE_TARGET_SAME;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.common.exception.ErrorCodeException;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;
import org.tb.order.service.EmployeeorderService;

@ExtendWith(MockitoExtension.class)
class MoveTimereportsServiceTest {

    @InjectMocks
    private MoveTimereportsService classUnderTest;
    @Mock
    private TimereportDAO timereportDAO;
    @Mock
    private TimereportRepository timereportRepository;
    @Mock
    private SuborderDAO suborderDAO;
    @Mock
    private EmployeeorderDAO employeeorderDAO;
    @Mock
    private EmployeeorderService employeeorderService;
    @Mock
    private EmployeecontractService employeecontractService;

    private static final long SOURCE_ID = 1L;
    private static final long TARGET_ID = 2L;
    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO = LocalDate.of(2024, 1, 31);

    private Suborder suborder(long id, LocalDate from, LocalDate until) {
        var so = new Suborder();
        ReflectionTestUtils.setField(so, "id", id);
        so.setFromDate(from);
        so.setUntilDate(until);
        return so;
    }

    private TimereportDTO dto(long id, long ecId, String employeeName, LocalDate date) {
        return TimereportDTO.builder()
                .id(id)
                .employeecontractId(ecId)
                .employeeName(employeeName)
                .referenceday(date)
                .build();
    }

    private Timereport timereportWithId(long id) {
        var tr = new Timereport();
        ReflectionTestUtils.setField(tr, "id", id);
        return tr;
    }

    @Nested
    class Validation {

        @Test
        void sameSourceAndTarget_throwsSameSuborderError() {
            var so = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(so);

            assertThatThrownBy(() ->
                classUnderTest.preview(SOURCE_ID, SOURCE_ID, List.of(), FROM, TO))
                .isInstanceOf(ErrorCodeException.class)
                .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
                    .anyMatch(m -> m.getErrorCode() == TR_MOVE_SOURCE_TARGET_SAME));
        }

        @Test
        void dateRangeStartsBeforeTargetFrom_throwsDateRangeError() {
            var source = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 15), null);
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(source);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);

            assertThatThrownBy(() ->
                classUnderTest.preview(SOURCE_ID, TARGET_ID, List.of(), FROM, TO))
                .isInstanceOf(ErrorCodeException.class)
                .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
                    .anyMatch(m -> m.getErrorCode() == TR_MOVE_DATE_RANGE_OUTSIDE_TARGET));
        }

        @Test
        void dateRangeEndsAfterTargetUntil_throwsDateRangeError() {
            var source = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 20));
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(source);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);

            assertThatThrownBy(() ->
                classUnderTest.preview(SOURCE_ID, TARGET_ID, List.of(), FROM, TO))
                .isInstanceOf(ErrorCodeException.class)
                .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
                    .anyMatch(m -> m.getErrorCode() == TR_MOVE_DATE_RANGE_OUTSIDE_TARGET));
        }

        @Test
        void openEndedTarget_dateRangeWithinFrom_passes() {
            var source = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), null);
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(source);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of());

            var preview = classUnderTest.preview(SOURCE_ID, TARGET_ID, List.of(), FROM, TO);

            assertThat(preview.timereports()).isEmpty();
        }
    }

    @Nested
    class Preview {

        @Test
        void timereports_sortedByEmployeeNameThenDate() {
            var source = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), null);
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(source);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of(
                dto(3L, 10L, "Zimmermann", LocalDate.of(2024, 1, 5)),
                dto(1L, 10L, "Zimmermann", LocalDate.of(2024, 1, 1)),
                dto(2L, 20L, "Andersen", LocalDate.of(2024, 1, 3))
            ));
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(anyLong(), eq(TARGET_ID)))
                .thenReturn(List.of(new Employeeorder()));

            var preview = classUnderTest.preview(SOURCE_ID, TARGET_ID, List.of(), FROM, TO);

            assertThat(preview.timereports()).extracting(TimereportDTO::getEmployeeName)
                .containsExactly("Andersen", "Zimmermann", "Zimmermann");
            assertThat(preview.timereports()).extracting(TimereportDTO::getReferenceday)
                .containsExactly(LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5));
        }

        @Test
        void noExistingEmployeeOrderOnTarget_listsNewEmployeeOrderWithEarliestDate() {
            var source = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(source);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of(
                dto(1L, 10L, "Mustermann", LocalDate.of(2024, 1, 10)),
                dto(2L, 10L, "Mustermann", LocalDate.of(2024, 1, 5))
            ));
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(10L, TARGET_ID))
                .thenReturn(List.of());

            var preview = classUnderTest.preview(SOURCE_ID, TARGET_ID, List.of(), FROM, TO);

            assertThat(preview.newEmployeeOrders()).hasSize(1);
            var neo = preview.newEmployeeOrders().getFirst();
            assertThat(neo.employeeFullName()).isEqualTo("Mustermann");
            assertThat(neo.fromDate()).isEqualTo(LocalDate.of(2024, 1, 5));
            assertThat(neo.untilDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        }

        @Test
        void noExistingEmployeeOrderOnTarget_openEndedSuborder_newOrderHasNullUntilDate() {
            var source = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), null);
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(source);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of(
                dto(1L, 10L, "Mustermann", LocalDate.of(2024, 1, 10))
            ));
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(10L, TARGET_ID))
                .thenReturn(List.of());

            var preview = classUnderTest.preview(SOURCE_ID, TARGET_ID, List.of(), FROM, TO);

            assertThat(preview.newEmployeeOrders()).hasSize(1);
            assertThat(preview.newEmployeeOrders().getFirst().untilDate()).isNull();
        }

        @Test
        void existingEmployeeOrderOnTarget_noNewEmployeeOrdersListed() {
            var source = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), null);
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(source);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of(
                dto(1L, 10L, "Mustermann", LocalDate.of(2024, 1, 10))
            ));
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(10L, TARGET_ID))
                .thenReturn(List.of(new Employeeorder()));

            var preview = classUnderTest.preview(SOURCE_ID, TARGET_ID, List.of(), FROM, TO);

            assertThat(preview.newEmployeeOrders()).isEmpty();
        }

        @Test
        void employeeContractFilter_onlyIncludesMatchingEmployees() {
            var source = suborder(SOURCE_ID, LocalDate.of(2023, 1, 1), null);
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), null);
            when(suborderDAO.getSuborderById(SOURCE_ID)).thenReturn(source);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of(
                dto(1L, 10L, "Mustermann", LocalDate.of(2024, 1, 10)),
                dto(2L, 20L, "Andersen", LocalDate.of(2024, 1, 5))
            ));
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(10L, TARGET_ID))
                .thenReturn(List.of());

            var preview = classUnderTest.preview(SOURCE_ID, TARGET_ID, List.of(10L), FROM, TO);

            assertThat(preview.timereports()).hasSize(1);
            assertThat(preview.timereports().getFirst().getEmployeeName()).isEqualTo("Mustermann");
        }
    }

    @Nested
    class Move {

        @Test
        void existingEmployeeOrderOnTarget_timereportReassignedToExistingOrder() {
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), null);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of(
                dto(100L, 10L, "Mustermann", LocalDate.of(2024, 1, 10))
            ));
            var existingEo = new Employeeorder();
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(10L, TARGET_ID))
                .thenReturn(List.of(existingEo));
            var timereport = timereportWithId(100L);
            when(timereportRepository.findAllById(any())).thenReturn(List.of(timereport));

            classUnderTest.move(SOURCE_ID, TARGET_ID, List.of(), FROM, TO);

            verify(employeeorderService, never()).create(any());
            assertThat(timereport.getSuborder()).isSameAs(target);
            assertThat(timereport.getEmployeeorder()).isSameAs(existingEo);
        }

        @Test
        void noExistingEmployeeOrderOnTarget_createsNewOrderWithEarliestDateAndAssigns() {
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of(
                dto(100L, 10L, "Mustermann", LocalDate.of(2024, 1, 10)),
                dto(101L, 10L, "Mustermann", LocalDate.of(2024, 1, 15))
            ));
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(10L, TARGET_ID))
                .thenReturn(List.of());
            var contract = new Employeecontract();
            when(employeecontractService.getEmployeecontractById(10L)).thenReturn(contract);
            var tr1 = timereportWithId(100L);
            var tr2 = timereportWithId(101L);
            when(timereportRepository.findAllById(any())).thenReturn(List.of(tr1, tr2));

            classUnderTest.move(SOURCE_ID, TARGET_ID, List.of(), FROM, TO);

            var captor = ArgumentCaptor.forClass(Employeeorder.class);
            verify(employeeorderService).create(captor.capture());
            var createdEo = captor.getValue();
            assertThat(createdEo.getFromDate()).isEqualTo(LocalDate.of(2024, 1, 10));
            assertThat(createdEo.getUntilDate()).isEqualTo(LocalDate.of(2024, 12, 31));
            assertThat(createdEo.getSuborder()).isSameAs(target);
            assertThat(createdEo.getEmployeecontract()).isSameAs(contract);
            assertThat(tr1.getSuborder()).isSameAs(target);
            assertThat(tr2.getSuborder()).isSameAs(target);
        }

        @Test
        void multipleEmployees_createsOrderOnlyForEmployeesMissingOne() {
            var target = suborder(TARGET_ID, LocalDate.of(2024, 1, 1), null);
            when(suborderDAO.getSuborderById(TARGET_ID)).thenReturn(target);
            when(timereportDAO.getTimereportsByDatesAndSuborderId(FROM, TO, SOURCE_ID)).thenReturn(List.of(
                dto(100L, 10L, "Andersen", LocalDate.of(2024, 1, 5)),
                dto(101L, 20L, "Mustermann", LocalDate.of(2024, 1, 10))
            ));
            var existingEo = new Employeeorder();
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(10L, TARGET_ID))
                .thenReturn(List.of(existingEo));
            when(employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(20L, TARGET_ID))
                .thenReturn(List.of());
            when(employeecontractService.getEmployeecontractById(20L)).thenReturn(new Employeecontract());
            var tr1 = timereportWithId(100L);
            var tr2 = timereportWithId(101L);
            when(timereportRepository.findAllById(any())).thenReturn(List.of(tr1, tr2));

            classUnderTest.move(SOURCE_ID, TARGET_ID, List.of(), FROM, TO);

            var captor = ArgumentCaptor.forClass(Employeeorder.class);
            verify(employeeorderService).create(captor.capture());
            assertThat(captor.getValue().getFromDate()).isEqualTo(LocalDate.of(2024, 1, 10));
            assertThat(tr1.getEmployeeorder()).isSameAs(existingEo);
        }
    }
}
