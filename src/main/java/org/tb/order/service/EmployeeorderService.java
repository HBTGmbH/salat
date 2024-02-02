package org.tb.order.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeorderService {

  private final EmployeeorderDAO employeeorderDAO;

  public void adjustValidity(Employeeorder employeeorder, LocalDate fromDate, LocalDate untilDate) {
    boolean changed = false;
    if (employeeorder.getFromDate().isBefore(fromDate)) {
      employeeorder.setFromDate(fromDate);
      changed = true;
    }
    if (employeeorder.getUntilDate() != null && employeeorder.getUntilDate().isBefore(fromDate)) {
      employeeorder.setUntilDate(fromDate);
      changed = true;
    }
    if (untilDate != null) {
      if (employeeorder.getFromDate().isAfter(untilDate)) {
        employeeorder.setFromDate(untilDate);
        changed = true;
      }
      if (employeeorder.getUntilDate() == null || employeeorder.getUntilDate().isAfter(untilDate)) {
        employeeorder.setUntilDate(untilDate);
        changed = true;
      }
    }
    if (changed) {
      employeeorderDAO.save(employeeorder);
    }
  }
  
}
