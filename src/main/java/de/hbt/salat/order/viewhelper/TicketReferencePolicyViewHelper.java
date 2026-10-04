package de.hbt.salat.order.viewhelper;

import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import de.hbt.salat.order.domain.TicketReferencePolicy;

/**
 * A ticket reference setting in words (#1326) — "keine", "höchstens 2", "beliebig viele". One place,
 * because the suborder form, the booking form and the inline edit of the daily view all name it.
 */
@Component
@RequiredArgsConstructor
public class TicketReferencePolicyViewHelper {

  private final MessageSourceAccessor messages;

  public String label(TicketReferencePolicy policy) {
    if (policy == null) {
      return "";
    }
    return switch (policy.mode()) {
      case NONE -> messages.getMessage("main.ticketreferences.policy.none");
      case LIMITED -> messages.getMessage("main.ticketreferences.policy.limited", new Object[] {policy.limit()});
      case UNLIMITED -> messages.getMessage("main.ticketreferences.policy.unlimited");
    };
  }
}
