package de.hbt.salat.secret.domain;

/**
 * The content of a secret with the version it was read at (#1417, → ADR-0033), for an owner that
 * writes it back only if nobody else has in between — the renewal of OAuth tokens.
 */
public record VersionedSecret(SecretValue value, SecretStatus status, int version) {

  @Override
  public String toString() {
    return "VersionedSecret[value=" + value + ", status=" + status + ", version=" + version + "]";
  }
}
