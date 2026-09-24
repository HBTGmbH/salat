package org.tb.dailyreport.domain;

import java.util.List;

/**
 * What the page needs to render its filter (#1092) — the two select boxes, and the current selection of the two
 * dialogs so their summary can be labelled.
 *
 * <p>The choices of the dialogs are not in here. In production they are seven thousand suborders and some thirty-six
 * thousand tickets; they are searched for when a dialog opens, not carried in every page.
 *
 * @param employees         whose bookings can show up — only ever those the user may read
 * @param customers         their customers
 * @param selectedOrders    the orders the filter currently names
 * @param selectedSuborders the suborders it names
 * @param selectedTickets   the ticket keys it names, as they were picked
 * @param includedSuborders how many suborders that selection brings along, descendants included
 */
public record TimereportFilterOptions(
    List<EmployeeOption> employees,
    List<CustomerOption> customers,
    List<OrderOption> selectedOrders,
    List<SuborderOption> selectedSuborders,
    List<String> selectedTickets,
    int includedSuborders
) {

  public static TimereportFilterOptions empty() {
    return new TimereportFilterOptions(List.of(), List.of(), List.of(), List.of(), List.of(), 0);
  }

  public record EmployeeOption(long id, String name, String sign) {}

  public record CustomerOption(long id, String shortname, String name) {}

  public record OrderOption(long id, String sign, String description, long customerId, String customerShortname,
                            int suborderCount) {}

  /**
   * @param level          how deep in the tree, {@code 0} directly under the order — the indentation of the dialog
   * @param descendantCount how many suborders a pick of this one brings along; the dialog says the number out loud
   *                        instead of offering a switch, because leaving them out would find nothing: bookings hang
   *                        on the leaf
   */
  public record SuborderOption(long id, String completeSign, String description, long customerOrderId, Long parentId,
                               int level, int descendantCount) {}

  /**
   * @param parentKey the ticket above this one, {@code null} at the top
   * @param orderIds  the orders whose scope this ticket was replicated under; the dialog only ever asks for the
   *                  scopes of the orders the filter names, so this stays empty unless a caller needs it
   */
  public record TicketOption(String key, String summary, String issueType, String parentKey, List<Long> orderIds) {}
}
