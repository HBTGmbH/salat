package de.hbt.salat.order.viewhelper;

import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import de.hbt.salat.order.domain.TagChoice;

/**
 * How often a tag is used, as the second line of its option (#847): {@code 3 Aufträge · 1 Unterauftrag}. Offered
 * while typing, the count makes the existing tag the obvious pick over a new one that differs by a hyphen.
 *
 * <p>Used from templates as {@code ${@tagViewHelper.usage(t)}} (→ ADR-0017).
 */
@Component
@RequiredArgsConstructor
public class TagViewHelper {

  private final MessageSourceAccessor messages;

  public String usage(TagChoice tag) {
    var parts = new ArrayList<String>();
    if (tag.customerorders() > 0) {
      parts.add(messages.getMessage("main.tag.usage.customerorders", new Object[] {tag.customerorders()}));
    }
    if (tag.suborders() > 0) {
      parts.add(messages.getMessage("main.tag.usage.suborders", new Object[] {tag.suborders()}));
    }
    return parts.isEmpty() ? messages.getMessage("main.tag.usage.none") : String.join(" · ", parts);
  }
}
