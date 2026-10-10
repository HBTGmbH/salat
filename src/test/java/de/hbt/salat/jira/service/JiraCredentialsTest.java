package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The ways of signing in at JIRA (#1385, #1417), and that no secret reaches a log line. */
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
  void an_oauth_access_token_is_sent_as_bearer_token_to_the_atlassian_api_of_the_site() {
    var credentials = JiraCredentials.oauth("access", "cloud-1", true);

    assertThat(credentials.authorizationHeader()).isEqualTo("Bearer access");
    assertThat(credentials.baseUrl("https://example.atlassian.net")).isEqualTo("https://api.atlassian.com/ex/jira/cloud-1");
  }

  @Test
  void without_oauth_the_calls_go_to_the_base_url() {
    assertThat(JiraCredentials.basic("user", "pw").baseUrl("https://jira.example.com"))
        .isEqualTo("https://jira.example.com");
  }

  @Test
  void the_string_form_leaves_the_secret_out() {
    assertThat(JiraCredentials.basic("user", "s3cret")).hasToString("JiraCredentials[method=BASIC, username=user]");
    assertThat(JiraCredentials.personalAccessToken("s3cret").toString()).doesNotContain("s3cret");
    assertThat(JiraCredentials.oauth("s3cret", "cloud-1", false).toString()).doesNotContain("s3cret");
  }
}
