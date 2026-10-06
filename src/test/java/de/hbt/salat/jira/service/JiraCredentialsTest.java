package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The two ways of signing in at JIRA (#1385), and that neither secret reaches a log line. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraCredentialsTest {

  @Test
  void basic_sends_user_and_password_base64_encoded() {
    assertThat(JiraCredentials.basic("user", "pw").authorizationHeader()).isEqualTo("Basic dXNlcjpwdw==");
  }

  @Test
  void a_personal_access_token_is_sent_as_bearer_token() {
    assertThat(JiraCredentials.personalAccessToken("tok").authorizationHeader()).isEqualTo("Bearer tok");
  }

  @Test
  void the_string_form_leaves_the_secret_out() {
    assertThat(JiraCredentials.basic("user", "s3cret")).hasToString("JiraCredentials[method=BASIC, username=user]");
    assertThat(JiraCredentials.personalAccessToken("s3cret").toString()).doesNotContain("s3cret");
  }
}
