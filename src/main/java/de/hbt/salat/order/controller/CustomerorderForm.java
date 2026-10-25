package de.hbt.salat.order.controller;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.TicketReferenceMode;

@Getter
@Setter
public class CustomerorderForm {

  private Long id;
  private Long customerId;
  /** the customer acting as intermediary (#1536), {@code null} for none */
  private Long intermediaryId;
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
  private List<Long> responsibleHbtIds = new ArrayList<>();
  private List<Long> storedResponsibleHbtIds = new ArrayList<>();
  /** the names of the tags (#847); a name typed into the field arrives the same way as a chosen one */
  private List<String> tagNames = new ArrayList<>();
  private Long respContrEmployeeId;
  /** Employee ID as stored in the DB when the edit form was opened; never mutated by the form lifecycle. */
  private Long storedRespContrEmployeeId;
  private OrderType orderType;
  private TicketReferenceMode ticketReferenceMode;
  private Integer ticketReferenceLimit;
  /** time surcharge rates (#1469) */
  private SurchargeFields surchargeRates = new SurchargeFields();

}
