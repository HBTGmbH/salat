package de.hbt.salat.secret.domain;

/** A single token, a Personal Access Token for instance (#1432). */
public record Token(String token) implements SecretValue {

  @Override
  public SecretType type() {
    return SecretType.TOKEN;
  }

  @Override
  public String toString() {
    return "Token[]";
  }
}
