package org.tb.reporting.domain;

public record JobExecutionResult(String jobName, int rowCount, String recipientEmails, boolean suppressed) {
}
