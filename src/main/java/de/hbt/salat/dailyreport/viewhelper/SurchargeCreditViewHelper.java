package de.hbt.salat.dailyreport.viewhelper;

import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.dailyreport.domain.MatrixData;
import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.viewhelper.SurchargeViewHelper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

/**
 * The time surcharge credit of bookings in words (#1469): the badge at a booking, the sums, the matrix. What
 * counts is how much a booking adds to the time account, so the badge shows the credit and names kind and
 * rate in its title only. The rates themselves are {@link SurchargeViewHelper}'s.
 */
@Component
@RequiredArgsConstructor
public class SurchargeCreditViewHelper {

  private final MessageSourceAccessor messages;
  private final SurchargeViewHelper surchargeViewHelper;

  /** The credit with its sign, "+1:30" — the badge, the matrix row and the sums. */
  public String credit(Duration credit) {
    return DurationUtils.formatSigned(credit != null ? credit : Duration.ZERO);
  }

  /** {@link #credit}, but empty without any — the share of a balance that has none. */
  public String creditOrEmpty(Duration credit) {
    return credit == null || credit.isZero() ? "" : credit(credit);
  }

  /** The title of a flagged booking's badge, "Zuschlag: Sonntag 100 % +2:30". */
  public String title(SurchargeKind kind, Integer percent, Duration credit) {
    return messages.getMessage("main.surcharge.badge.title",
        new Object[] {surchargeViewHelper.flag(kind, percent), credit(credit)});
  }

  /**
   * One booking behind a matrix cell in words, "2:00: ABC-12 Kommentar (Zuschlag +2:00)": the ticket references
   * before the comment, as they stood in it before #1326, the time surcharge after it.
   */
  public String matrixDetail(MatrixData.ReportDetail detail) {
    var text = new StringBuilder(String.join(" ", detail.ticketReferences()));
    if (detail.taskDescription() != null && !detail.taskDescription().isBlank()) {
      text.append(text.isEmpty() ? "" : " ").append(detail.taskDescription().strip());
    }
    if (detail.surchargeKind() != null) {
      text.append(text.isEmpty() ? "" : " ")
          .append('(').append(messages.getMessage("main.surcharge.named", new Object[] {credit(detail.surchargeCredit())}))
          .append(')');
    }
    return text.isEmpty() ? detail.durationString() : detail.durationString() + ": " + text;
  }
}
