package de.hbt.salat.dailyreport.controller;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * An order the booking form offers when the suborder is chosen in two steps (#1522): first the order,
 * then one of its suborders.
 *
 * @param label   sign and short description, as every order select lists it
 * @param subtext the customer, searched as well
 */
public record BookableOrderOption(Long id, String label, String subtext) {

    /**
     * The orders of the bookable suborders, each once and in the order of the list — so the first order
     * is the one of the first suborder, which the form preselects without a favourite. Derived rather
     * than asked for: an order without a suborder to book on the day must not be offered.
     */
    public static List<BookableOrderOption> of(List<SuborderOption> suborders) {
        var orders = new LinkedHashMap<Long, BookableOrderOption>();
        suborders.forEach(s -> orders.putIfAbsent(s.orderId(),
            new BookableOrderOption(s.orderId(), s.orderLabel(), s.orderSubtext())));
        return List.copyOf(orders.values());
    }
}
