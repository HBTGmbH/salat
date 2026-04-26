package org.tb.order.controller;

import lombok.Getter;
import lombok.Setter;
import org.tb.order.domain.OrderType;

@Getter
@Setter
public class CustomerorderForm {

  private Long id;
  private Long customerId;
  private String sign;
  private String description;
  private String shortdescription;
  private String validFrom;
  private String validUntil;
  private String responsibleCustomerTechnical;
  private String responsibleCustomerContractually;
  private String orderCustomer;
  private String debithours;
  private Byte debithoursunit;
  private Boolean hide;
  private Long employeeId;
  private Long respContrEmployeeId;
  private OrderType orderType;

}
