package de.hbt.salat.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The links in a mail are absolute (#1505): an address without scheme is a relative path to a mail
 * program, and the default {@code localhost} produced exactly that.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SalatPropertiesTest {

  @Test
  void keeps_a_configured_scheme() {
    assertThat(withUrl("http://localhost:8080").absoluteUrl("/settings")).isEqualTo("http://localhost:8080/settings");
  }

  @Test
  void adds_https_to_an_address_without_scheme() {
    assertThat(withUrl("salat.example.org").absoluteUrl("/settings")).isEqualTo("https://salat.example.org/settings");
  }

  @Test
  void joins_with_exactly_one_slash() {
    assertThat(withUrl("https://salat.example.org/").absoluteUrl("/settings")).isEqualTo("https://salat.example.org/settings");
    assertThat(withUrl("https://salat.example.org").absoluteUrl("settings")).isEqualTo("https://salat.example.org/settings");
  }

  private static SalatProperties withUrl(String url) {
    var properties = new SalatProperties();
    properties.setUrl(url);
    return properties;
  }
}
