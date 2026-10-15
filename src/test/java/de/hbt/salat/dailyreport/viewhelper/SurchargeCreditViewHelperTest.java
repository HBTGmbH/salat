package de.hbt.salat.dailyreport.viewhelper;

import static de.hbt.salat.order.domain.SurchargeKind.SATURDAY;
import static de.hbt.salat.order.domain.SurchargeKind.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import de.hbt.salat.dailyreport.domain.MatrixData.ReportDetail;
import de.hbt.salat.order.viewhelper.SurchargeViewHelper;

/**
 * The time surcharge credit of a booking in words (#1469): the badge shows the credit, its title kind and rate;
 * the details of a matrix cell name the tickets before the comment and the surcharge after it.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SurchargeCreditViewHelperTest {

  /** The bundle keeps number and percent sign together. */
  private static final String NBSP = "\u00a0";

  private final SurchargeCreditViewHelper viewHelper;

  SurchargeCreditViewHelperTest() {
    var messages = messages();
    viewHelper = new SurchargeCreditViewHelper(messages, new SurchargeViewHelper(messages));
  }

  @Test
  void the_badge_of_a_booking_shows_the_credit_and_names_kind_and_rate_in_its_title() {
    assertThat(viewHelper.credit(Duration.ofMinutes(150))).isEqualTo("+2:30");
    assertThat(viewHelper.title(SUNDAY, 100, Duration.ofMinutes(150))).isEqualTo("Zuschlag: Sonntag 100" + NBSP + "% +2:30");
  }

  @Test
  void a_share_without_any_credit_is_empty() {
    assertThat(viewHelper.creditOrEmpty(Duration.ofMinutes(90))).isEqualTo("+1:30");
    assertThat(viewHelper.creditOrEmpty(Duration.ZERO)).isEmpty();
    assertThat(viewHelper.creditOrEmpty(null)).isEmpty();
  }

  @Test
  void the_detail_of_a_matrix_cell_names_tickets_comment_and_surcharge() {
    var detail = new ReportDetail("3:00", " Einspielen ", List.of("ABC-12", "ABC-13"), SATURDAY, 50, Duration.ofMinutes(90));

    assertThat(viewHelper.matrixDetail(detail)).isEqualTo("3:00: ABC-12 ABC-13 Einspielen (Zuschlag +1:30)");
  }

  @Test
  void a_detail_without_anything_to_say_is_its_duration() {
    assertThat(viewHelper.matrixDetail(new ReportDetail("1:00", "", List.of(), null, null, Duration.ZERO)))
        .isEqualTo("1:00");
    assertThat(viewHelper.matrixDetail(new ReportDetail("2:00", null, List.of(), SATURDAY, 50, Duration.ofHours(1))))
        .isEqualTo("2:00: (Zuschlag +1:00)");
  }

  private static MessageSourceAccessor messages() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(messageSource, Locale.GERMANY);
  }
}
