package org.tb.jira.service;

import java.time.LocalDate;

/**
 * What a worklog says: a day and a number of minutes. Nothing else — no person, no task
 * description (#1007). The sum is over everybody who booked on that ticket that day.
 */
public record JiraWorklogEntry(LocalDate workDate, int minutes) {

}
