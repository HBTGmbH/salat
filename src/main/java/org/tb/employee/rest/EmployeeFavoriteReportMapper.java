package org.tb.employee.rest;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.employee.domain.EmployeeFavoriteReport;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.Suborder.VisitorDirection;
import org.tb.order.persistence.EmployeeorderDAO;

@Component
@RequiredArgsConstructor
public class EmployeeFavoriteReportMapper {

  private final EmployeeorderDAO employeeorderDAO;

  EmployeeFavoriteReportDTO toTarget(EmployeeFavoriteReport s) {
    return EmployeeFavoriteReportDTO.builder()
        .id(s.getId())
        .employeeorderId(s.getEmployeeorder().getId())
        .comment(s.getDescription())
        .hours(s.getDurationhours())
        .minutes(s.getDurationminutes())
        .orderLabelPath(createOrderTree(s.getEmployeeorder().getSuborder()))
        .orderSignPath(createSignTree(s.getEmployeeorder().getSuborder()))
        .build();
  }

  private List<String> createSignTree(Suborder s) {
    List<String> res = new ArrayList<>();
    s.acceptVisitor(suborder -> {
      if (res.isEmpty()) {
        Customerorder customerorder = suborder.getCustomerorder();
        res.add(customerorder.getSign());
      }
      res.add(s.getSign());
    }, VisitorDirection.PARENT);
    return res;
  }

  private List<String> createOrderTree(Suborder s) {
    List<String> res = new ArrayList<>();
    s.acceptVisitor(suborder -> {
      if (res.isEmpty()) {
        Customerorder customerorder = suborder.getCustomerorder();
        res.add(customerorder.getShortdescription());
      }
      res.add(s.getShortdescription());
    }, VisitorDirection.PARENT);
    return res;
  }


  EmployeeFavoriteReport toTarget(EmployeeFavoriteReportDTO s) {
    Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(
        s.getEmployeeorderId());
    return EmployeeFavoriteReport.builder()
        .id(s.getId())
        .employeeorder(employeeorder)
        .employee(employeeorder.getEmployeecontract().getEmployee())
        .description(s.getComment())
        .durationhours(s.getHours())
        .durationminutes(s.getMinutes())
        .build();
  }

  List<EmployeeFavoriteReportDTO> toTarget(List<EmployeeFavoriteReport> res) {
    return res.stream().map(this::toTarget).toList();
  }
}
