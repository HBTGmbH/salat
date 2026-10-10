package de.hbt.salat.secret.domain;

/**
 * What a secret holds (#1432, → ADR-0038 §1). Each type is one unit that is always written as a
 * whole. {@code KEY} comes with its first user. The refresh token of an OAuth connection is a
 * {@link Token} (#1417): the access token lives for one run and is never stored.
 *
 * <p>Stored in plain text in {@code secret.type}, and part of the associated data of the cipher: a
 * payload does not decrypt under another type.
 */
public enum SecretType {

  /** User name and password — {@link UsernamePassword}. */
  USERNAME_PASSWORD,

  /** A single token — {@link Token}. */
  TOKEN
}
