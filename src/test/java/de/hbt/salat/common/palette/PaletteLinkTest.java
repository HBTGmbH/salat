package de.hbt.salat.common.palette;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The hrefs of the palette's targets (#1157). A filter text is free text: a slash, a space or an
 * ampersand in it has to arrive as part of the value, not end the path segment or start the next parameter.
 *
 * <p>{@code null} goes out as the empty value rather than being left out, because a filter a link
 * names overwrites what the list remembers (ADR-0022) — {@code fCustomerId=} is "no customer", a
 * missing {@code fCustomerId} is "the one remembered".
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteLinkTest {

  @Test
  void leaves_a_path_without_parameters_as_it_is() {
    assertThat(PaletteLink.to("/budget").build()).isEqualTo("/budget");
  }

  @Test
  void starts_the_query_with_a_question_mark_and_joins_the_parameters_with_ampersands() {
    assertThat(PaletteLink.to("/budget/controlling")
        .param("fBudgetCustomerOrderId", 42L)
        .param("evaluate", true)
        .build())
        .isEqualTo("/budget/controlling?fBudgetCustomerOrderId=42&evaluate=true");
  }

  @Test
  void writes_numbers_and_booleans_as_their_text() {
    assertThat(PaletteLink.to("/orders/suborders").param("fCustomerOrderId", 42L).param("evaluate", false).build())
        .isEqualTo("/orders/suborders?fCustomerOrderId=42&evaluate=false");
  }

  @Test
  void encodes_a_slash_in_a_value() {
    assertThat(PaletteLink.to("/customers").param("fCustomerFilter", "MUSTER/01").build())
        .isEqualTo("/customers?fCustomerFilter=MUSTER%2F01");
  }

  /** Form encoding, which the servlet container reads back as a space. */
  @Test
  void encodes_a_space_in_a_value() {
    assertThat(PaletteLink.to("/customers").param("fCustomerFilter", "MUSTER 01").build())
        .isEqualTo("/customers?fCustomerFilter=MUSTER+01");
  }

  @Test
  void encodes_an_ampersand_so_that_it_does_not_start_another_parameter() {
    assertThat(PaletteLink.to("/customers")
        .param("fCustomerFilter", "MUSTER&01")
        .param("fCustomerId", 7L)
        .build())
        .isEqualTo("/customers?fCustomerFilter=MUSTER%2601&fCustomerId=7");
  }

  @Test
  void encodes_every_character_that_would_change_the_meaning_of_the_query() {
    assertThat(PaletteLink.to("/customers").param("fCustomerFilter", "1+1=2?#50%").build())
        .isEqualTo("/customers?fCustomerFilter=1%2B1%3D2%3F%2350%25");
  }

  @Test
  void encodes_non_ascii_characters_as_utf_8() {
    assertThat(PaletteLink.to("/customers").param("fCustomerFilter", "Möbel").build())
        .isEqualTo("/customers?fCustomerFilter=M%C3%B6bel");
  }

  @Test
  void sends_null_as_the_empty_value() {
    assertThat(PaletteLink.to("/orders/suborders")
        .param("fCustomerOrderId", 42L)
        .param("fCustomerId", null)
        .param("fSuborderFilter", null)
        .build())
        .isEqualTo("/orders/suborders?fCustomerOrderId=42&fCustomerId=&fSuborderFilter=");
  }

  @Test
  void adds_a_conditional_parameter_only_where_its_condition_holds() {
    assertThat(PaletteLink.to("/orders/customerorders")
        .param("fCustomerOrderFilter", "MUSTER-01")
        .paramIf(true, "fCustomerOrderShowInactive", true)
        .paramIf(false, "fCustomerOrderShowHidden", true)
        .build())
        .isEqualTo("/orders/customerorders?fCustomerOrderFilter=MUSTER-01&fCustomerOrderShowInactive=true");
  }

  @Test
  void writes_no_question_mark_where_no_condition_holds() {
    assertThat(PaletteLink.to("/orders/suborders")
        .paramIf(false, "fSuborderShowInactive", true)
        .paramIf(false, "fSuborderShowHidden", true)
        .build())
        .isEqualTo("/orders/suborders");
  }

  @Test
  void encodes_a_conditional_parameter_like_any_other() {
    assertThat(PaletteLink.to("/orders/suborders")
        .paramIf(true, "fSuborderFilter", "MUSTER/01 A&B")
        .paramIf(true, "fCustomerId", null)
        .build())
        .isEqualTo("/orders/suborders?fSuborderFilter=MUSTER%2F01+A%26B&fCustomerId=");
  }
}
