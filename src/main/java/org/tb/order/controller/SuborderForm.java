package org.tb.order.controller;

import lombok.Getter;
import lombok.Setter;
import org.tb.order.domain.OrderType;

@Getter
@Setter
public class SuborderForm {

  private Long id;
  private Long customerorderId;
  private String sign;
  private String description;
  private String shortdescription;
  private String suborder_customer;
  /** Invoice: "Y" = yes, "N" = no, "U" = undefined. String avoids primitive char binding issues. */
  private String invoice;
  private Boolean standard;
  private Boolean commentnecessary;
  private Boolean fixedPrice;
  private Boolean trainingFlag;
  private String validFrom;
  private String validUntil;
  private String debithours;
  private Byte debithoursunit;
  private Boolean hide;
  private Long parentId;
  private OrderType orderType;

}
