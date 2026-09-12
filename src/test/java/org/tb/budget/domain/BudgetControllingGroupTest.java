package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * A group is exactly one budget plan, which is why the plan's progress lives here rather than on one
 * of its rows (#989). The controlling header reads it from the group.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingGroupTest {

  @Test
  public void should_report_progress_and_status_of_its_plan() {
    var group = group(40.0, ProgressStatus.BEHIND);

    assertThat(group.hasProgress()).isTrue();
    assertThat(group.hasProgressStatus()).isTrue();
    // Decimal separator follows the default locale, which the test must not depend on.
    assertThat(group.progressFormatted()).matches("40[.,]0 %");
  }

  /** A plan without a progress mode has none — the header then says nothing about it. */
  @Test
  public void should_report_no_progress_without_a_percentage() {
    var group = group(null, null);

    assertThat(group.hasProgress()).isFalse();
    assertThat(group.hasProgressStatus()).isFalse();
    assertThat(group.progressFormatted()).isEqualTo("—");
  }

  /**
   * How far the plan has come is known, but there is no budget to judge it against. The progress is
   * still worth showing — only the verdict is missing, so the two are answered separately.
   */
  @Test
  public void should_report_progress_without_a_status_when_there_is_no_budget() {
    var group = group(40.0, ProgressStatus.UNKNOWN);

    assertThat(group.hasProgress()).isTrue();
    assertThat(group.hasProgressStatus()).isFalse();
    assertThat(group.progressFormatted()).matches("40[.,]0 %");
  }

  private static BudgetControllingGroup group(Double progressPercent, ProgressStatus status) {
    return new BudgetControllingGroup("co/01", "plan", List.of(), null, progressPercent, status);
  }
}
