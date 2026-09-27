package org.tb.customer.controller;

import static org.tb.common.palette.PaletteKind.CUSTOMER;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.Hiding;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteLink;
import org.tb.common.palette.PaletteProvider;
import org.tb.common.palette.PaletteQuery;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;
import org.tb.customer.domain.CustomerSearchRow;
import org.tb.customer.service.CustomerService;

/**
 * Customers for the object search of the command palette (#1157, ADR-0031).
 *
 * <p>Only managers have a page for a single customer (the edit form); everybody else who is not
 * restricted sees every customer on the list, so "open" is the list narrowed to the customer.
 * Restricted users see no list ({@code requireUnrestricted} on the controller) and get no hit.
 *
 * <p>The target "its orders" is a plain URL of the order module, which this module may not import;
 * the parameter names are the ones {@code OrderUiStateKeyContributor} registers.
 */
@Component
@RequiredArgsConstructor
public class CustomerPaletteProvider implements PaletteProvider {

  private final CustomerService customerService;
  private final AuthorizedUser authorizedUser;

  @Override
  public List<PaletteHit> search(PaletteQuery query) {
    if (authorizedUser.isRestricted()) {
      return List.of();
    }
    return customerService.getPaletteCandidates(query).stream()
        // a customer without short name and name has nothing to be shown by
        .filter(row -> present(row.shortname()) || present(row.name()))
        .map(row -> hit(query, row))
        .toList();
  }

  private PaletteHit hit(PaletteQuery query, CustomerSearchRow row) {
    var hidden = Hiding.isHidden(row.hide());
    var shortname = present(row.shortname()) ? row.shortname() : row.name();
    var name = present(row.name()) && !row.name().equals(shortname) ? row.name() : null;
    var open = authorizedUser.isManager()
        ? PaletteLink.to("/customers/edit").param("id", row.id())
        : PaletteLink.to("/customers").param("fCustomerFilter", shortname)
            .paramIf(hidden, "fCustomerShowHidden", true);
    // the orders of a hidden customer are hidden as a rule; without the switch the list stays empty
    var orders = PaletteLink.to("/orders/customerorders")
        .param("fCustomerId", row.id())
        .param("fCustomerOrderFilter", null)
        .paramIf(hidden, "fCustomerOrderShowHidden", true);
    var targets = List.of(
        new PaletteTarget(PaletteText.of("main.palette.target.customer.open"), open.build(), PaletteTarget.OPEN),
        new PaletteTarget(PaletteText.of("main.palette.target.customer.orders"), orders.build(), 1));
    return new PaletteHit(CUSTOMER, String.valueOf(row.id()), shortname, name, null, false, hidden,
        query.match(shortname, row.name()), targets);
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
