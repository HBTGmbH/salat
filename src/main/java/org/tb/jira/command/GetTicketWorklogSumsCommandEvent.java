package org.tb.jira.command;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.tb.common.command.CommandEvent;

/**
 * Asks for the booked minutes per day and ticket reference, for a set of suborders and a period
 * (#1007). The jira module needs them to write the worklogs; the bookings live in dailyreport,
 * which the import direction does not allow jira to reach — so the answer comes back through a
 * command event, answered by a listener in {@code TimereportService}.
 *
 * <p>The suborders are named by id, not the scope by its sign: resolving a scope to a branch of the
 * order tree is what the jira module does anyway (it administers that scope since #1025), and
 * duplicating the resolution on the answering side would put the same rule in two places.
 *
 * @param suborderIds every suborder the scope covers, the branch below it included
 * @param from first day to sum, inclusive
 * @param until last day to sum, inclusive
 */
@Builder
@Data
@AllArgsConstructor
public class GetTicketWorklogSumsCommandEvent implements CommandEvent<List<TicketDaySum>> {

  private final List<Long> suborderIds;
  private final LocalDate from;
  private final LocalDate until;
  private List<TicketDaySum> result;

  @Override
  public List<TicketDaySum> getResult() {
    return result;
  }

  @Override
  public void setResult(List<TicketDaySum> result) {
    this.result = result;
  }
}
