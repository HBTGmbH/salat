package org.tb.dailyreport.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.util.DateUtils;

@Getter
@Setter
public class MoveTimereportsForm {

  private Long sourceCustomerOrderId;
  private Long sourceSuborderId;
  private Long targetCustomerOrderId;
  private Long targetSuborderId;
  private List<Long> employeeContractIds = new ArrayList<>();
  private String fromDate;
  private String toDate;

  public LocalDate getFromDateTyped() {
    return DateUtils.parseOrNull(fromDate);
  }

  public LocalDate getToDateTyped() {
    return DateUtils.parseOrNull(toDate);
  }
}
