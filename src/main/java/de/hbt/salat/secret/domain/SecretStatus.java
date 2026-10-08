package de.hbt.salat.secret.domain;

/**
 * The state of a secret (#1432, → ADR-0038 §2). A secret has no {@code hide}: it is either usable
 * or has to be renewed, and it is deleted for good when its owner goes.
 */
public enum SecretStatus {

  VALID,

  /** The connection behind the secret has to be established again. Set by OAuth (#1417). */
  REAUTH_REQUIRED
}
