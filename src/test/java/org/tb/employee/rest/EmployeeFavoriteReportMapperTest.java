package org.tb.employee.rest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.EmployeeFavoriteReport;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;

@ExtendWith(MockitoExtension.class)
@Import({EmployeeFavoriteReportMapper.class})
class EmployeeFavoriteReportMapperTest {

  @Mock
  EmployeeorderDAO employeeorderDAO;

  @InjectMocks
  EmployeeFavoriteReportMapper favoriteReportMapper;

  @Test
  void toDTO() throws IllegalAccessException {
    EmployeeFavoriteReport source = EmployeeFavoriteReport.builder()
        .description("EmployeeFavoriteReport description")
        .durationhours(123)
        .durationminutes(214)
        .employeeorder(Employeeorder.builder().suborder(Suborder.builder()
            .shortdescription("Suborder shortdescription")
            .customerorder(Customerorder.builder()
                .shortdescription("Customerorder shortdescription")
                .build())
            .build()).build())
        .build();
    FieldUtils.writeField(source.getEmployeeorder(), "id", 1234L, true);
    FieldUtils.writeField(source.getEmployeeorder().getSuborder(), "id", 4321L, true);
    EmployeeFavoriteReportDTO res = favoriteReportMapper.toTarget(source);
    assertEquals(source.getEmployeeorder().getId(), res.getEmployeeorderId());
    assertEquals(source.getDurationminutes(), res.getMinutes());
    assertEquals(source.getDurationhours(), res.getHours());
    assertEquals(source.getDescription(), res.getComment());
    assertThat(res.getOrderLabelPath().size()).isEqualTo(2);
  }

  @Test
  void fromDTO() throws IllegalAccessException {
    // GIVEN
    Employeeorder employeeorder = Employeeorder.builder().suborder(Suborder.builder()
            .shortdescription("Suborder shortdescription")
            .customerorder(Customerorder.builder()
                .shortdescription("Customerorder shortdescription")
                .build())
            .build())
        .employeecontract(Employeecontract.builder().employee(Employee.builder().build()).build())
        .build();
    EmployeeFavoriteReportDTO source = EmployeeFavoriteReportDTO.builder()
        .comment("EmployeeFavoriteReport description")
        .minutes(123)
        .hours(214)
        .employeeorderId(1234L)
        .build();
    when(employeeorderDAO.getEmployeeorderById(anyLong())).thenReturn(employeeorder);
    FieldUtils.writeField(employeeorder, "id", 1234L, true);
    FieldUtils.writeField(employeeorder.getSuborder(), "id", 4321L, true);
    // WHEN
    EmployeeFavoriteReport res = favoriteReportMapper.toTarget(source);
    // THEN
    assertEquals(source.getEmployeeorderId(), res.getEmployeeorder().getId());
    assertEquals(source.getMinutes(), res.getDurationminutes());
    assertEquals(source.getHours(), res.getDurationhours());
    assertEquals(source.getComment(), res.getDescription());
    assertThat(res.getEmployee()).isNotNull();
    assertThat(res.getEmployee()).isNotNull();
  }
}