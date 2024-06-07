package org.tb.dailyreport.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.auth.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;

import java.time.LocalDate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkingDayRestEndpointTest {
    @Mock
    EmployeecontractDAO employeecontractDAO;

    @Mock
    WorkingdayDAO workingdayDAO;

    @Mock
    AuthorizedUser authorizedUser;

    @Captor
    ArgumentCaptor<Workingday> workingDayArgumentCaptor;

    @InjectMocks
    WorkingDayRestEndpoint workingDayRestEndpoint;

    @BeforeEach
    void initAuthorizedUser() {
        when(authorizedUser.isAuthenticated()).thenReturn(true);
    }

    @Test
    void shouldCreateWorkingDay() {
        // given
        var employeeContract = employeeContract();
        var data = WorkingDayData.builder()
                .starthour(7)
                .startminute(8)
                .breakhours(9)
                .breakminutes(10)
                .date("2024-07-06")
                .build();
        when(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(anyLong(), any(LocalDate.class)))
                .thenReturn(employeeContract);

        // when
        workingDayRestEndpoint.upsert(data);

        // then
        verify(workingdayDAO, times(1)).save(workingDayArgumentCaptor.capture());
        assertThat(workingDayArgumentCaptor.getValue())
                .isNotNull()
                .hasFieldOrPropertyWithValue("Starttimehour", 7)
                .hasFieldOrPropertyWithValue("Starttimeminute", 8)
                .hasFieldOrPropertyWithValue("Breakhours", 9)
                .hasFieldOrPropertyWithValue("Breakminutes", 10)
                .hasFieldOrPropertyWithValue("refday", DateUtils.parse("2024-07-06"))
                .hasFieldOrPropertyWithValue("employeecontract", employeeContract);
    }

    @Test
    void shouldGetWorkingDay() {
        // given
        var employeeContract = employeeContract();
        var workingDay = new Workingday();
        workingDay.setStarttimehour(7);
        workingDay.setRefday(DateUtils.parse("2024-07-06"));
        when(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(anyLong(), any(LocalDate.class)))
                .thenReturn(employeeContract);
        when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(any(LocalDate.class), anyLong()))
                .thenReturn(workingDay);

        // when
        var result = workingDayRestEndpoint.get("2024-07-06");

        // then
        assertThat(result)
                .isNotNull()
                .hasFieldOrPropertyWithValue("starthour", 7)
                .hasFieldOrPropertyWithValue("date", "2024-07-06");
    }

    // fixtures

    private Employeecontract employeeContract() {
        Employeecontract res = new Employeecontract();
        ReflectionTestUtils.setField(res, "id", (long) (Math.random() * 100));
        return res;
    }
}