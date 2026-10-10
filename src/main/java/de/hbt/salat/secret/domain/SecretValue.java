package de.hbt.salat.secret.domain;

/**
 * The content of a secret in plain text (#1432), as {@code SecretService} hands it out and takes it
 * in. Every implementation leaves the secret out of its {@code toString()}: a value like this ends
 * up in log lines and exception messages sooner or later.
 */
public sealed interface SecretValue permits UsernamePassword, Token, OAuthTokens {

  SecretType type();
}
