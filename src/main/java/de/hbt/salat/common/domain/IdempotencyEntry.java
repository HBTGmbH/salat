package de.hbt.salat.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A creating call that has been carried out, under the key its caller gave it (#1532, concept 6.5): a repetition
 * with the same key answers with what was created then instead of creating it again. Transaction data, owned by
 * nobody but the login that used the key; the person is kept as the id of the login, never its name (#1204).
 */
@Entity
@Table(name = "idempotency_key",
    uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key_user_key", columnNames = {"user_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private long userId;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  /** What was called, e.g. {@code createTimereport}: the same key for another operation is a mistake of the caller. */
  @Column(nullable = false, length = 100)
  private String operation;

  /** SHA-256 of the input, to tell a repetition from a second call that reuses the key with other data. */
  @Column(name = "input_hash", nullable = false, length = 64)
  private String inputHash;

  @Column(name = "result_id", nullable = false)
  private long resultId;

  @Column(nullable = false)
  private LocalDateTime created;

  public IdempotencyEntry(long userId, String idempotencyKey, String operation, String inputHash, long resultId,
      LocalDateTime created) {
    this.userId = userId;
    this.idempotencyKey = idempotencyKey;
    this.operation = operation;
    this.inputHash = inputHash;
    this.resultId = resultId;
    this.created = created;
  }
}
