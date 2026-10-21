package de.hbt.salat.order.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.order.domain.TagChoice;
import de.hbt.salat.tag.domain.TagColor;

/** The tag filter of the lists and the tag choice of the forms (#847). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderTagsHelperTest {

  /** UiState keeps one value per parameter: the ids arrive comma-separated, and a stale or forged value is ignored. */
  @Test
  void the_filter_reads_the_ids_and_ignores_the_rest() {
    assertThat(OrderTags.filterIds("3, 7,x,,3,-1,99999999999999999999")).containsExactly(3L, 7L);
    assertThat(OrderTags.filterIds(null)).isEmpty();
    assertThat(OrderTags.filterIds(" ")).isEmpty();
    assertThat(OrderTags.filterValue(List.of(3L, 7L))).isEqualTo("3,7");
  }

  /** A name typed into a form that failed for another reason stays chosen on the way back. */
  @Test
  void a_name_of_the_form_that_is_no_tag_yet_is_offered_as_well() {
    var tags = List.of(new TagChoice(1, "Festpreis", TagColor.BLUE, 2, 1));

    var choices = OrderTags.formChoices(tags, List.of("festpreis", " Neuer  Tag ", ""));

    assertThat(choices).extracting(TagChoice::name).containsExactly("Festpreis", "Neuer Tag");
    assertThat(choices.get(1).color()).isNull();
  }
}
