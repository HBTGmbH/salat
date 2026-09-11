package org.tb.dailyreport.controller;

/**
 * @param customerorderSign the sign of the customer order the suborder belongs to — the booking form
 *                          renders it so that the ticket suggestions can be looked up for it (#982)
 */
record SuborderOption(Long id, String label, String subtext, boolean commentNecessary,
                      boolean trainingFlag, String customerorderSign) {}
