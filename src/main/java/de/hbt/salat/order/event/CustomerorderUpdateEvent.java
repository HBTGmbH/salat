package de.hbt.salat.order.event;

import lombok.Getter;
import de.hbt.salat.common.event.DomainObjectUpdateEvent;
import de.hbt.salat.order.domain.Customerorder;

@Getter
public class CustomerorderUpdateEvent extends DomainObjectUpdateEvent<Customerorder> {

  /**
   * The sign the order carried before this update (#1205) — the order itself already carries the new
   * one. Records that still name the order by sign follow a rename by it; {@code null} where it is
   * not known.
   */
  private final String previousSign;

  public CustomerorderUpdateEvent(Customerorder source) {
    this(source, null);
  }

  public CustomerorderUpdateEvent(Customerorder source, String previousSign) {
    super(source);
    this.previousSign = previousSign;
  }

}
