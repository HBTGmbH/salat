package de.hbt.salat.favorites.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static de.hbt.salat.testutils.ReferenceTestUtils.employeeorderWithId;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.favorites.domain.Favorite;

/**
 * A favourite carries several ticket references (#1326); a client from before that knows one
 * reference only and keeps working with the first.
 */
class FavoriteDTOMapperTest {

  private final FavoriteDTOMapper mapper = Mappers.getMapper(FavoriteDTOMapper.class);

  @Test
  void reading_gives_all_references_and_the_first_one_alone() {
    var favorite = Favorite.builder().employeeorder(employeeorderWithId(7L)).hours(1).minutes(30).comment("Daily")
        .ticketReferences(new ArrayList<>(List.of("ABC-1", "ABC-2"))).build();

    var dto = mapper.map(favorite);

    assertThat(dto.getTicketReferences()).containsExactly("ABC-1", "ABC-2");
    assertThat(dto.getTicketReference()).isEqualTo("ABC-1");
    assertThat(dto.getEmployeeorderId()).isEqualTo(7L);
    assertThat(dto.getHours()).isEqualTo(1);
    assertThat(dto.getMinutes()).isEqualTo(30);
    assertThat(dto.getComment()).isEqualTo("Daily");
  }

  @Test
  void a_favourite_without_references_reads_as_none_in_both_fields() {
    var dto = mapper.map(Favorite.builder().employeeorder(employeeorderWithId(7L)).hours(1).minutes(0).build());

    assertThat(dto.getTicketReferences()).isEmpty();
    assertThat(dto.getTicketReference()).isNull();
  }

  @Test
  void writing_takes_the_list_where_there_is_one() {
    var dto = FavoriteDTO.builder().employeeorderId(7L).hours(1).ticketReference("OLD-1")
        .ticketReferences(List.of("ABC-1", "ABC-2")).build();

    assertThat(mapper.map(dto).ticketReferences()).containsExactly("ABC-1", "ABC-2");
  }

  @Test
  void writing_without_a_list_takes_the_single_reference_of_an_older_client() {
    var dto = FavoriteDTO.builder().employeeorderId(7L).hours(1).ticketReference("OLD-1").build();

    assertThat(mapper.map(dto).ticketReferences()).containsExactly("OLD-1");
  }

  /** The employee order is what the favourite belongs to (#1369); one is required. */
  @Test
  void writing_takes_the_employee_order_and_requires_one() {
    assertThat(mapper.map(FavoriteDTO.builder().employeeorderId(7L).hours(1).build()).employeeorderId())
        .isEqualTo(7L);
    assertThatThrownBy(() -> mapper.map(FavoriteDTO.builder().hours(1).build()))
        .isInstanceOf(ResponseStatusException.class);
  }
}
