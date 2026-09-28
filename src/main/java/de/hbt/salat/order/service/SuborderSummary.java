package de.hbt.salat.order.service;

public record SuborderSummary(Long id, String completeOrderSign,
                               String shortdescription, boolean commentNecessary,
                               boolean trainingFlag) {}
