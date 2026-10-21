package de.hbt.salat.achievement.domain;

import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.employee.domain.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A level of an achievement a person has reached (#1501): which, on which day, and whether the
 * person has seen it announced. Recorded once by the nightly job; afterwards only {@link #seenAt}
 * changes. A yearly achievement records its levels once per year (#1512), every other one once with
 * {@link de.hbt.salat.common.achievement.AchievementDefinition#NO_YEAR}.
 */
@Entity
@Table(name = "achievement", uniqueConstraints = @UniqueConstraint(
    name = "uk_achievement_employee_key_year_level",
    columnNames = {"employee_id", "achievement_key", "achievement_year", "achievement_level"}))
@Getter
@Setter
@NoArgsConstructor
public class Achievement extends AuditedEntity {

  public static final int KEY_MAX_LENGTH = 64;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "fk_achievement_employee"))
  private Employee employee;

  @Column(name = "achievement_key", nullable = false, length = KEY_MAX_LENGTH)
  private String achievementKey;

  @Column(name = "achievement_level", nullable = false)
  private int achievementLevel;

  /** The calendar year of a yearly achievement's level; {@code NO_YEAR} for every other one. */
  @Column(name = "achievement_year", nullable = false)
  private int achievementYear;

  /** The day it was reached — the day of the matter, not the day it was recorded. */
  @Column(name = "achieved_on", nullable = false)
  private LocalDate achievedOn;

  /**
   * When the person saw it announced, or when it was recorded while the person had announcements
   * switched off; {@code null} until then.
   */
  @Column(name = "seen_at")
  private LocalDateTime seenAt;
}
