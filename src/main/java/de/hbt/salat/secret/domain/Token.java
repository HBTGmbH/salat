package de.hbt.salat.secret.domain;

/** A single token: a Personal Access Token (#1432), or the refresh token of an OAuth connection (#1417). */
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
