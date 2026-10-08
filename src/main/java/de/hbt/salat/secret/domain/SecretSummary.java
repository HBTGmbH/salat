package de.hbt.salat.secret.domain;

/**
 * A secret as a form gets to see it (#1432): everything but the secret itself.
 *
 * @param readable whether the secret can be decrypted with the keys the application has. It cannot
 *     in a copy of the database taken from another environment (→ ADR-0038 §4), and it cannot
 *     without any key at all.
 * @param username the user name of a {@link UsernamePassword}; {@code null} for other types and for
 *     a secret that is not readable
 */
public record SecretSummary(long id, SecretType type, SecretStatus status, boolean readable, String username) {

}
