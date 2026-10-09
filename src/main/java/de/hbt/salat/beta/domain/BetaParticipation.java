package de.hbt.salat.beta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.employee.domain.Employee;

/**
 * A person's part in a beta (#1447): when they switched it on and off, and where they stand with the
 * questions about it. The switch itself stays a preference of the login ({@link BetaFeatures}); this
 * row records what the evaluation needs from it — how many tried the beta, how many went back, after
 * how long.
 *
 * <p>It holds no answer: a feedback is stored without the person ({@link BetaFeedback}).
 */
@Entity
@Table(name = "beta_participation", uniqueConstraints = @UniqueConstraint(name = "uk_beta_participation",
    columnNames = {"feature_key", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class BetaParticipation extends AuditedEntity {

  @Column(name = "feature_key", nullable = false, length = BetaUsage.KEY_MAX_LENGTH)
  private String featureKey;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false,
      foreignKey = @ForeignKey(name = "fk_beta_participation_employee"))
  private Employee employee;

  @Column(name = "first_enabled_at", nullable = false)
  private LocalDateTime firstEnabledAt;

  /** When the person switched the beta on last. */
  @Column(name = "enabled_at", nullable = false)
  private LocalDateTime enabledAt;

  /** When the person switched it off last; {@code null} while it has never been switched off. */
  @Column(name = "disabled_at")
  private LocalDateTime disabledAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "feedback_state", nullable = false, length = 16)
  private FeedbackState feedbackState = FeedbackState.OPEN;

  /** From when a postponed question is asked again. */
  @Column(name = "feedback_ask_after")
  private LocalDate feedbackAskAfter;

  /** The person switched the beta off and has not yet been asked why. */
  @Column(name = "switch_off_pending", nullable = false)
  private boolean switchOffPending;

  /** The id of the person; reading it does not load the employee. */
  public Long getEmployeeId() {
    return employee == null ? null : employee.getId();
  }

  public boolean isEnabled() {
    return disabledAt == null || enabledAt.isAfter(disabledAt);
  }
}
