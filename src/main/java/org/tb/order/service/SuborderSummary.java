package org.tb.order.service;

public record SuborderSummary(Long id, String completeOrderSign,
                               String shortdescription, boolean commentNecessary) {}
