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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.employee.domain.Employee;

/**
 * How often a person used one event of a beta on one day, on one side of it (#1447). One row per
 * person, day, beta, event and {@link BetaVariant}; a further use raises {@code useCount}. The rows
 * stay with the person so that the evaluation can tell how many people stand behind a number — it
 * shows none of them, only sums over at least three.
 *
 * <p>The person is master data of the employee module (ADR-0036): a read-only reference.
 */
@Entity
@Table(name = "beta_usage", uniqueConstraints = @UniqueConstraint(name = "uk_beta_usage",
    columnNames = {"feature_key", "event_key", "employee_id", "usage_date", "variant"}))
@Getter
@Setter
@NoArgsConstructor
public class BetaUsage extends AuditedEntity {

  public static final int KEY_MAX_LENGTH = 64;

  @Column(name = "feature_key", nullable = false, length = KEY_MAX_LENGTH)
  private String featureKey;

  @Column(name = "event_key", nullable = false, length = KEY_MAX_LENGTH)
  private String eventKey;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "fk_beta_usage_employee"))
  private Employee employee;

  @Column(name = "usage_date", nullable = false)
  private LocalDate usageDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private BetaVariant variant;

  @Column(name = "use_count", nullable = false)
  private int useCount;
}
