package de.hbt.salat.secret.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import de.hbt.salat.common.domain.AuditedEntity;

/**
 * A secret, encrypted (#1432, → ADR-0038). Type and status are the only plain text; the content —
 * user name included — is in {@link #payload}.
 *
 * <p>The entity never leaves the module: its fields mean nothing without the key, and the content
 * goes out decrypted through {@code SecretService} alone. An owner therefore remembers the id with a
 * foreign key, not a reference (ADR-0038, deviating from ADR-0036). A secret does not know its owner.
 *
 * <p>Master data of its owner (ADR-0011) with a status instead of {@code hide}, and deleted for good:
 * a secret that is only marked as deleted is still stored.
 *
 * <p>Not in the second level cache: there is no reason to keep secrets in yet another place.
 */
@Entity
@Table(name = "secret")
@Getter
@Setter
@NoArgsConstructor
public class Secret extends AuditedEntity {

  /** IV and authentication tag come on top of the content — room for long tokens as well. */
  public static final int PAYLOAD_MAX_LENGTH = 8192;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 32)
  private SecretType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private SecretStatus status = SecretStatus.VALID;

  /** The key {@link #payload} is encrypted with, as named in {@code salat.secret.keys}. */
  @Column(name = "key_id", nullable = false, length = 32)
  private String keyId;

  /** The 96-bit IV, followed by the cipher text and the authentication tag. */
  @Column(name = "payload", nullable = false, length = PAYLOAD_MAX_LENGTH)
  private byte[] payload;

  /**
   * Binds the payload to this row (ADR-0038 §3): a cipher text copied into another row fails to
   * decrypt instead of quietly handing out the secret of another connection. Hence a secret is
   * encrypted only once it has its id.
   */
  public String associatedData() {
    return "secret:" + getId() + ":" + type.name();
  }
}
