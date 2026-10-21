package de.hbt.salat.tag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.tag.domain.TagColor;
import de.hbt.salat.tag.domain.TagName;
import de.hbt.salat.tag.domain.TagOption;
import de.hbt.salat.tag.persistence.TagRepository;

/**
 * Free tagging produces {@code Festpreis}, {@code festpreis} and {@code Festpreis } side by side (#847); the service
 * keeps them one tag, against the database and within one call.
 */
@DataJpaTest
@Import({AuthorizedUserAuditorAware.class, TagService.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class TagServiceTest {

  @Autowired
  private TagService tagService;

  @Autowired
  private TagRepository tagRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  void a_new_name_becomes_a_tag_with_the_colour_of_its_name() {
    var ids = tagService.resolveTagIds(List.of("Festpreis"));

    var tag = tagRepository.findById(ids.getFirst()).orElseThrow();
    assertThat(tag.getName()).isEqualTo("Festpreis");
    assertThat(tag.getNameKey()).isEqualTo("festpreis");
    assertThat(tag.getColor()).isEqualTo(TagColor.of(TagName.of("Festpreis")));
  }

  @Test
  void a_name_that_differs_only_in_case_and_white_space_is_the_existing_tag() {
    var existing = tagService.resolveTagIds(List.of("Fest Preis")).getFirst();

    assertThat(tagService.resolveTagIds(List.of("  fest   PREIS "))).containsExactly(existing);
    assertThat(tagRepository.count()).isEqualTo(1);
    assertThat(tagRepository.findById(existing).orElseThrow().getName()).isEqualTo("Fest Preis");
  }

  @Test
  void two_spellings_in_the_same_call_become_one_tag() {
    var ids = tagService.resolveTagIds(List.of("Festpreis", " festpreis", "Cloud"));

    assertThat(ids).hasSize(2).doesNotHaveDuplicates();
    assertThat(tagRepository.count()).isEqualTo(2);
  }

  @Test
  void an_empty_name_is_no_tag() {
    assertThat(tagService.resolveTagIds(Arrays.asList("", "   ", null))).isEmpty();
    assertThat(tagRepository.count()).isZero();
  }

  @Test
  void the_options_are_ordered_by_name_regardless_of_case() {
    tagService.resolveTagIds(List.of("cloud", "Betrieb", "Abnahme"));

    assertThat(tagService.getTagOptions()).extracting(TagOption::name).containsExactly("Abnahme", "Betrieb", "cloud");
  }

  @Test
  void a_name_longer_than_the_column_is_refused() {
    assertRefused(() -> tagService.resolveTagIds(List.of("x".repeat(101))), ErrorCode.TG_NAME_TOO_LONG);
    assertThat(tagService.resolveTagIds(List.of("x".repeat(100)))).hasSize(1);
  }

  /** The tables are utf8mb3: a character outside the BMP would fail in the database instead. */
  @Test
  void a_name_with_an_emoji_is_refused() {
    assertRefused(() -> tagService.resolveTagIds(List.of("Cloud 🚀")), ErrorCode.TG_NAME_UNSUPPORTED_CHARACTER);
  }

  /** Whoever may tag creates tags: management, as the editing of orders and suborders (#847). */
  @Test
  void creating_tags_is_management_only_reading_them_is_open_to_every_unrestricted_login() throws Exception {
    var resolve = TagService.class.getMethod("resolveTagIds", java.util.Collection.class).getAnnotation(Authorized.class);
    var service = TagService.class.getAnnotation(Authorized.class);

    assertThat(resolve.requiresManager()).isTrue();
    assertThat(service.requireUnrestricted()).isTrue();
    assertThat(TagService.class.getMethod("getTagOptions").getAnnotation(Authorized.class)).isNull();
  }

  private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOf(InvalidDataException.class)
        .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
            .anySatisfy(message -> assertThat(message.getErrorCode()).isEqualTo(code)));
  }
}
