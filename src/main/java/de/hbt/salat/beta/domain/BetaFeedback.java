package de.hbt.salat.beta.domain;

import static lombok.AccessLevel.PRIVATE;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * An answer to the question about a beta (#1447), <strong>without the person</strong> (ADR-0039).
 *
 * <p>That is why it does not extend {@code AuditedEntity}: its {@code createdby} would name the
 * person, and its timestamps would tie the answer to the moment the person's
 * {@link BetaParticipation} changed. The week is all the evaluation needs. Whoever reads the
 * database directly could still guess from the order of the rows; the evaluation page shows neither
 * order nor week.
 */
@Entity
@Table(name = "beta_feedback")
@Getter
@Setter
@NoArgsConstructor
public class BetaFeedback implements Persistable<Long> {

  public static final int COMMENT_MAX_LENGTH = 1000;
  public static final int RATING_MIN = 1;
  public static final int RATING_MAX = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(PRIVATE)
  private Long id;

  @Column(name = "feature_key", nullable = false, length = BetaUsage.KEY_MAX_LENGTH)
  private String featureKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "feedback_trigger", nullable = false, length = 16)
  private FeedbackTrigger trigger;

  /** 1 (not helpful) to 5 (very helpful); {@code null} when the person left it out. */
  @Column(name = "rating")
  private Integer rating;

  @Column(name = "comment_text", length = COMMENT_MAX_LENGTH)
  private String commentText;

  /** The ISO week of the answer, as {@code 2026-W41}. */
  @Column(name = "iso_week", nullable = false, length = 8)
  private String isoWeek;

  @Override
  public boolean isNew() {
    return id == null;
  }
}
