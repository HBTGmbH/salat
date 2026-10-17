package de.hbt.salat.favorites.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.favorites.domain.FavoriteEntry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * A favourite carries several ticket references (#1326); a client from before that knows one
 * reference only and keeps working with the first.
 */
@SuppressWarnings("deprecation")
class FavoriteDTOMapperTest {

  private final FavoriteDTOMapper mapper = Mappers.getMapper(FavoriteDTOMapper.class);

  @Test
  void reading_gives_all_references_and_the_first_one_alone() {
    var favorite = new FavoriteEntry(5L, 7L, "ABC/01 - Wartung", Duration.ofHours(1).plusMinutes(30), "Daily", List.of("ABC-1", "ABC-2"),
        null, null, null);

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
    var dto = mapper.map(new FavoriteEntry(5L, 7L, "ABC/01 - Wartung", Duration.ofHours(1), null, null, null, null, null));

    assertThat(dto.getTicketReferences()).isEmpty();
    assertThat(dto.getTicketReference()).isNull();
  }

  /** Read only (#1414): a client can show the groups, it does not arrange them. */
  @Test
  void reading_names_the_group() {
    var dto = mapper.map(new FavoriteEntry(5L, 7L, "ABC/01 - Wartung", Duration.ofHours(1), null, null, 3L, "Wartung", null));

    assertThat(dto.getGroupName()).isEqualTo("Wartung");
  }

  /** Read, the duration is one value (#1398) and, for older clients, hours and minutes apart. */
  @Test
  void reading_gives_the_duration_and_splits_it_into_hours_and_minutes() {
    var dto = mapper.map(new FavoriteEntry(5L, 7L, "ABC/01 - Wartung", Duration.ofHours(1).plusMinutes(45), null,
        null, null, null, null));

    assertThat(dto.getDuration()).isEqualTo(Duration.ofHours(1).plusMinutes(45));
    assertThat(dto.getHours()).isEqualTo(1);
    assertThat(dto.getMinutes()).isEqualTo(45);
  }

  @Test
  void writing_takes_the_duration() {
    var dto = FavoriteDTO.builder().employeeorderId(7L).duration(Duration.ofHours(25).plusMinutes(30)).build();

    assertThat(mapper.map(dto).duration()).isEqualTo(Duration.ofHours(25).plusMinutes(30));
  }

  @Test
  void the_duration_wins_over_hours_and_minutes() {
    var dto = FavoriteDTO.builder().employeeorderId(7L).duration(Duration.ofMinutes(15)).hours(1).minutes(45).build();

    assertThat(mapper.map(dto).duration()).isEqualTo(Duration.ofMinutes(15));
  }

  /** In JSON the duration is HH:mm (#1398); the former fields come along and go in without it. */
  @Test
  void in_json_the_duration_is_hours_and_minutes() {
    var json = new ObjectMapper();
    var read = json.writeValueAsString(mapper.map(new FavoriteEntry(5L, 7L, "ABC/01 - Wartung", Duration.ofHours(1).plusMinutes(45),
        null, null, null, null, null)));

    assertThat(read).contains("\"duration\":\"01:45\"", "\"hours\":1", "\"minutes\":45");
    assertThat(json.readValue("{\"employeeorderId\":7,\"duration\":\"02:30\"}", FavoriteDTO.class).getDuration())
        .isEqualTo(Duration.ofHours(2).plusMinutes(30));
    assertThat(json.readValue("{\"employeeorderId\":7,\"hours\":2,\"minutes\":30}", FavoriteDTO.class).getDuration())
        .isEqualTo(Duration.ofHours(2).plusMinutes(30));
    assertThatThrownBy(() -> json.readValue("{\"employeeorderId\":7,\"duration\":\"02:60\"}", FavoriteDTO.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void writing_joins_hours_and_minutes_into_one_duration() {
    var dto = FavoriteDTO.builder().employeeorderId(7L).hours(1).minutes(45).build();

    assertThat(mapper.map(dto).duration()).isEqualTo(Duration.ofHours(1).plusMinutes(45));
  }

  @Test
  void a_minutes_part_of_sixty_or_more_adds_up() {
    var dto = FavoriteDTO.builder().employeeorderId(7L).hours(0).minutes(90).build();

    assertThat(mapper.map(dto).duration()).isEqualTo(Duration.ofHours(1).plusMinutes(30));
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
