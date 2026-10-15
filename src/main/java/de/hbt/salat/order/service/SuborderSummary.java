package de.hbt.salat.order.service;

import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.domain.SurchargeRate;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import java.util.Map;

/**
 * @param ticketReferencePolicy how many ticket references a booking on it may carry, as it applies
 *                              after inheritance (#1326)
 * @param surchargeRates the time surcharges a booking on it may be flagged with, per kind, as they
 *                       apply after inheritance; only the kinds offered (#1469)
 */
public record SuborderSummary(Long id, String completeOrderSign,
                               String shortdescription, boolean commentNecessary,
                               boolean trainingFlag, TicketReferencePolicy ticketReferencePolicy,
                               Map<SurchargeKind, SurchargeRate> surchargeRates) {}
