package de.hbt.salat.order.controller;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.TicketReferenceMode;

@Getter
@Setter
public class SuborderForm {

  private Long id;
  private Long customerId;
  private Long customerorderId;
  private String sign;
  private String description;
  private String shortdescription;
  private String suborder_customer;
  private Boolean invoice;
  private Boolean standard;
  private Boolean commentnecessary;
  private boolean trainingFlag;
  private String validFrom;
  private String validUntil;
  private String debithours;
  private Byte debithoursunit;
  private Boolean hide;
  private Long parentId;
  private OrderType orderType;
  /** {@code null} inherits from above (#1326) */
  private TicketReferenceMode ticketReferenceMode;
  private Integer ticketReferenceLimit;
  /** time surcharge rates (#1469) */
  private SurchargeFields surchargeRates = new SurchargeFields();
  /** the names of the tags (#847); a name typed into the field arrives the same way as a chosen one */
  private List<String> tagNames = new ArrayList<>();

}
