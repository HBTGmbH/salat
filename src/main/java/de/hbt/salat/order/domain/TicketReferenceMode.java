package de.hbt.salat.order.domain;

import lombok.Getter;

/** How many ticket references a booking on an order may carry (#1326), see {@link TicketReferencePolicy}. */
@Getter
public enum TicketReferenceMode {

  NONE("main.ticketreferences.mode.none"),
  LIMITED("main.ticketreferences.mode.limited"),
  UNLIMITED("main.ticketreferences.mode.unlimited");

  private final String label;

  TicketReferenceMode(String label) {
    this.label = label;
  }
}
