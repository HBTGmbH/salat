package de.hbt.salat.secret.domain;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * What an OAuth connection is, apart from its tokens (#1417, → ADR-0038 §1): the provider, the
 * foreign account the owner acts as, the resource it was made for and who made it when. Nothing in
 * here is a secret, but it is stored encrypted with the tokens, like the user name of a
 * {@link UsernamePassword}, and it is all a form gets to see of the connection.
 *
 * @param provider the registration in {@code salat.oauth.clients} the tokens are renewed with
 * @param accountId the id of the foreign account, as the provider names it
 * @param accountName the display name of the foreign account
 * @param resourceId the resource of the provider the connection was made for — the cloud id of an
 *     Atlassian site
 * @param resourceUrl the address of that resource — the URL of the Atlassian site
 * @param scopes the scopes the provider granted, which may be fewer than were asked for
 * @param connectedBy the sign of the person who connected
 * @param connectedAt when the connection was made
 */
public record OAuthConnection(
    String provider,
    String accountId,
    String accountName,
    String resourceId,
    String resourceUrl,
    Set<String> scopes,
    String connectedBy,
    LocalDateTime connectedAt
) {

  public OAuthConnection {
    scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
  }

  public boolean grants(String scope) {
    return scopes.contains(scope);
  }

  OAuthConnection withScopes(Set<String> granted) {
    return new OAuthConnection(provider, accountId, accountName, resourceId, resourceUrl, granted, connectedBy,
        connectedAt);
  }
}
