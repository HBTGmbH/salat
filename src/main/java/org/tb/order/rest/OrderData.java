package org.tb.order.rest;

import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
@Builder(toBuilder = true)
@RequiredArgsConstructor
@AllArgsConstructor
public class OrderData {

  private final Long id;
  private final String label;
  private final boolean commentRequired;
  @Setter
  private Collection<OrderData> suborder;
  private final long employeeorderId;

}
