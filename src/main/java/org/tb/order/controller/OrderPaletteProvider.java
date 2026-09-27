package org.tb.order.controller;

import static org.tb.common.palette.PaletteKind.CUSTOMERORDER;
import static org.tb.common.palette.PaletteKind.SUBORDER;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.Hiding;
import org.tb.common.Validity;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteLink;
import org.tb.common.palette.PaletteProvider;
import org.tb.common.palette.PaletteQuery;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.order.domain.CustomerorderSearchRow;
import org.tb.order.domain.SuborderSearchRow;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Customer orders and suborders for the object search of the command palette (#1157, ADR-0031).
 *
 * <p>A page for a single order or suborder exists only for managers: the edit forms are
 * {@code requiresManager}. Everybody else who is not restricted sees every order and suborder on the
 * list pages — there is no rule per order —, so for them "open" is the list, narrowed down to the
 * object. Restricted users see no list at all ({@code requireUnrestricted} on both controllers): for
 * them the provider only looks for the suborders they may book today and gives those no target of
 * its own; the booking module adds the one they can open, or the hit is not shown.
 *
 * <p>An order's hit is keyed by its sign, which the budget module needs for controlling and plans; a
 * suborder's by its id, which the booking form takes.
 */
@Component
@RequiredArgsConstructor
public class OrderPaletteProvider implements PaletteProvider {

  /** Longer descriptions are cut, as {@code getShortdescription()} of the entities does. */
  private static final int DESCRIPTION_LENGTH = 40;

  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;

  @Override
  public List<PaletteHit> search(PaletteQuery query) {
    var hits = new ArrayList<PaletteHit>();
    if (!authorizedUser.isRestricted()) {
      customerorderService.getPaletteCandidates(query).forEach(row -> hits.add(orderHit(query, row)));
    }
    hits.addAll(suborderHits(query));
    return hits;
  }

  private PaletteHit orderHit(PaletteQuery query, CustomerorderSearchRow row) {
    var ended = Validity.isInactive(row.untilDate());
    var hidden = Hiding.isHidden(row.hide());
    var description = shortened(row.shortdescription(), row.description());
    var customer = firstPresent(row.customerShortname(), row.customerName());
    var open = authorizedUser.isManager()
        ? PaletteLink.to("/orders/customerorders/edit").param("id", row.id())
        : PaletteLink.to("/orders/customerorders")
            .param("fCustomerOrderFilter", row.sign())
            .param("fCustomerId", row.customerId())
            .paramIf(ended, "fCustomerOrderShowInactive", true)
            .paramIf(hidden, "fCustomerOrderShowHidden", true);
    var suborders = PaletteLink.to("/orders/suborders")
        .param("fCustomerOrderId", row.id())
        .param("fCustomerId", null)
        .param("fSuborderFilter", null)
        .paramIf(ended, "fSuborderShowInactive", true);
    var targets = List.of(
        new PaletteTarget(PaletteText.of("main.palette.target.customerorder.open"), open.build(), PaletteTarget.OPEN),
        new PaletteTarget(PaletteText.of("main.palette.target.customerorder.suborders"), suborders.build(), 1));
    return new PaletteHit(CUSTOMERORDER, row.sign(), row.sign(), joined(description, customer), null,
        ended, hidden, query.match(row.sign(), row.shortdescription(), row.description(),
            row.customerShortname(), row.customerName()), targets);
  }

  private List<PaletteHit> suborderHits(PaletteQuery query) {
    Long bookableFor = null;
    if (authorizedUser.isRestricted()) {
      bookableFor = authorizedEmployee.getEmployeeId();
      if (bookableFor == null) {
        return List.of();
      }
    }
    var rows = suborderService.getPaletteCandidates(query, bookableFor);
    var completeSigns = suborderService.getCompleteOrderSigns(rows);
    return rows.stream().map(row -> suborderHit(query, row, completeSigns.get(row.id()))).toList();
  }

  private PaletteHit suborderHit(PaletteQuery query, SuborderSearchRow row, String completeSign) {
    var ended = Validity.isInactive(row.untilDate());
    // a suborder under a hidden order is no more on offer than the order itself
    var hidden = Hiding.isHidden(row.hide()) || Hiding.isHidden(row.customerorderHide());
    var targets = authorizedUser.isRestricted() ? List.<PaletteTarget>of() : List.of(
        new PaletteTarget(PaletteText.of("main.palette.target.suborder.open"),
            openSuborder(row, completeSign, ended), PaletteTarget.OPEN));
    return new PaletteHit(SUBORDER, String.valueOf(row.id()), completeSign, row.shortdescription(),
        row.customerShortname() == null ? null : PaletteText.of("main.palette.context.plain", row.customerShortname()),
        ended, hidden, query.match(completeSign, row.shortdescription(), row.customerorderShortdescription(),
            row.customerShortname()), targets);
  }

  private String openSuborder(SuborderSearchRow row, String completeSign, boolean ended) {
    if (authorizedUser.isManager()) {
      return PaletteLink.to("/orders/suborders/" + row.id() + "/edit").build();
    }
    return PaletteLink.to("/orders/suborders")
        .param("fCustomerOrderId", row.customerorderId())
        .param("fCustomerId", null)
        .param("fSuborderFilter", completeSign)
        .paramIf(ended, "fSuborderShowInactive", true)
        .paramIf(Hiding.isHidden(row.hide()), "fSuborderShowHidden", true)
        .build();
  }

  private static String shortened(String shortdescription, String description) {
    var text = firstPresent(shortdescription, description);
    if (text == null || text.length() <= DESCRIPTION_LENGTH) {
      return text;
    }
    return text.substring(0, DESCRIPTION_LENGTH) + "…";
  }

  private static String firstPresent(String... values) {
    return Stream.of(values).filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
        .findFirst().orElse(null);
  }

  private static String joined(String... parts) {
    var text = Stream.of(parts).filter(Objects::nonNull).collect(Collectors.joining(" · "));
    return text.isEmpty() ? null : text;
  }
}
