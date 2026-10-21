package de.hbt.salat.tag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The colour of a tag follows from its name (#847). {@code salat.js} computes the same rule to colour a tag typed into
 * a form before it is saved; the pairs here are the ones checked there in the browser. A change to the rule turns this
 * red — and has to be made in {@code salat.js} as well.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TagColorTest {

  @Test
  void the_colour_follows_from_the_name_regardless_of_case_and_white_space() {
    assertThat(TagColor.of(TagName.of("Festpreis"))).isEqualTo(TagColor.AZURE);
    assertThat(TagColor.of(TagName.of(" festpreis "))).isEqualTo(TagColor.AZURE);
    assertThat(TagColor.of(TagName.of("Wartung und Betrieb"))).isEqualTo(TagColor.ORANGE);
    assertThat(TagColor.of(TagName.of("Abnahme"))).isEqualTo(TagColor.LIME);
  }

  @Test
  void the_palette_is_the_order_the_colour_is_picked_from() {
    assertThat(TagColor.palette()).isEqualTo("blue,azure,indigo,purple,pink,red,orange,yellow,lime,green,teal,cyan");
  }
}
