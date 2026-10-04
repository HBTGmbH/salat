package de.hbt.salat.order.service;

import de.hbt.salat.order.domain.TicketReferencePolicy;

/**
 * @param ticketReferencePolicy how many ticket references a booking on it may carry, as it applies
 *                              after inheritance (#1326)
 */
public record SuborderSummary(Long id, String completeOrderSign,
                               String shortdescription, boolean commentNecessary,
                               boolean trainingFlag, TicketReferencePolicy ticketReferencePolicy) {}
