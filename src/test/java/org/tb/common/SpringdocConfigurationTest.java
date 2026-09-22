package org.tb.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Swagger UI announces a redirect URI to the identity provider, and only the ones registered there
 * are accepted. The springdoc default points at {@code /swagger-ui/}, not at the configured UI path
 * ({@code /api/doc/}), so a profile that leaves it out sends a URI that is not registered and
 * "Authorize" fails with a mismatch — visible only in the browser, against the deployed
 * environment. That is what #1048 was.
 *
 * <p>Whether the key is placed where anything reads it is checked by {@link
 * ConfigurationPropertyBindingTest}; this test is about it being there at all.
 */
class SpringdocConfigurationTest {

    private static final String SWAGGER_UI = "springdoc.swagger-ui.";

    @Test
    void profilesConfiguringSwaggerOAuthAlsoSetTheRedirectUrl() {
        Set<String> withOAuth = new TreeSet<>();
        Set<String> withRedirectUrl = new TreeSet<>();
        for (ProfileProperties.Entry entry : ProfileProperties.all()) {
            if (entry.name().equals(SWAGGER_UI + "oauth.client-id")) {
                withOAuth.add(entry.file());
            }
            if (entry.name().equals(SWAGGER_UI + "oauth2-redirect-url")) {
                withRedirectUrl.add(entry.file());
            }
        }

        assertThat(withOAuth)
            .describedAs("the profiles under test actually configure Swagger UI OAuth")
            .isNotEmpty();
        assertThat(withRedirectUrl)
            .describedAs("""
                a profile that configures Swagger UI OAuth names the redirect URI as well: \
                the springdoc default points at /swagger-ui/, not at the configured UI path, \
                and is not registered with the identity provider (#1048)""")
            .containsAll(withOAuth);
    }
}
