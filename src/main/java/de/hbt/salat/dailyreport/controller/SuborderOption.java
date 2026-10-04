package de.hbt.salat.dailyreport.controller;

import java.time.LocalDate;
import java.util.List;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.viewhelper.SuborderLabelViewHelper;

/**
 * A suborder the booking form offers.
 *
 * @param sign  the complete order sign alone, the key the command palette shows on its chip (#1158)
 * @param description the short description alone, what the command palette shows next to the chip
 * @param label the sign with the short description, as every suborder select lists it (#1266)
 * @param absence whether its order is an absence — sickness, vacation and the like — which the form
 *                books with what is left of the target of the day (#1214)
 * @param standby whether its order is standby, which is no working time and therefore does not move
 *                the end of the day the form shows for the duration entered (#1263)
 * @param ticketReferencePolicy how many ticket references a booking on it may carry (#1326)
 */
public record SuborderOption(Long id, String sign, String description, String label, String subtext,
                             boolean commentNecessary, boolean trainingFlag, boolean absence,
                             boolean standby, TicketReferencePolicy ticketReferencePolicy) {

    /**
     * What the contract can book on the day: the suborders of its employee orders valid then. The
     * booking form offers exactly these, and so does the palette's {@code buchen} (#1158) — one source,
     * so that the palette never proposes a suborder the form would not show.
     */
    public static List<SuborderOption> bookable(CustomerorderService customerorderService, SuborderService suborderService,
            long ecId, LocalDate date) {
        return customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date)
            .stream()
            .flatMap(order -> suborderService.getSuborderSummaries(ecId, order.getId(), date).stream()
                .map(s -> new SuborderOption(s.id(), s.completeOrderSign(),
                    s.shortdescription() == null || s.shortdescription().isBlank() ? null : s.shortdescription(),
                    SuborderLabelViewHelper.of(s.completeOrderSign(), s.shortdescription()),
                    SuborderLabelViewHelper.subtextOfOrder(order), s.commentNecessary(), s.trainingFlag(),
                    order.getOrderType() == OrderType.KRANK_URLAUB_ABWESEND,
                    order.getOrderType() == OrderType.BEREITSCHAFT,
                    s.ticketReferencePolicy())))
            .toList();
    }
}
