package de.hbt.salat.favorites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.testutils.ReferenceTestUtils.employeeorderWithId;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.favorites.domain.Favorite;
import de.hbt.salat.favorites.domain.NewFavorite;
import de.hbt.salat.favorites.persistence.EmployeeorderReferences;
import de.hbt.salat.favorites.persistence.FavoriteRepository;
import de.hbt.salat.order.service.EmployeeorderService;

/**
 * A favourite has no person of its own: it belongs to the person of its employee order (#1369).
 * Whose an employee order is, the order module answers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class FavoriteServiceTest {

  private static final long ME = 1L;
  private static final long SOMEBODY_ELSE = 2L;
  private static final long MY_ORDER = 10L;
  private static final long FOREIGN_ORDER = 20L;

  @Mock
  private FavoriteRepository favoriteRepository;
  @Mock
  private AuthorizedEmployee authorizedEmployee;
  @Mock
  private EmployeeorderReferences employeeorderReferences;
  @Mock
  private EmployeeorderService employeeorderService;

  @InjectMocks
  private FavoriteService favoriteService;

  @BeforeEach
  void setUp() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(ME);
    when(employeeorderService.getEmployeeIdOfEmployeeorder(MY_ORDER)).thenReturn(Optional.of(ME));
    when(employeeorderService.getEmployeeIdOfEmployeeorder(FOREIGN_ORDER)).thenReturn(Optional.of(SOMEBODY_ELSE));
    when(employeeorderReferences.employeeorder(any(Long.class)))
        .thenAnswer(invocation -> employeeorderWithId(invocation.<Long>getArgument(0)));
    when(favoriteRepository.save(any())).thenAnswer(invocation -> {
      Favorite favorite = invocation.getArgument(0);
      ReflectionTestUtils.setField(favorite, "id", 99L);
      return favorite;
    });
  }

  @Test
  void a_favourite_on_an_own_employee_order_refers_to_it() {
    var id = favoriteService.addFavorite(new NewFavorite(MY_ORDER, 1, 30, "Daily", List.of("abc-1")));

    var saved = ArgumentCaptor.forClass(Favorite.class);
    verify(favoriteRepository).save(saved.capture());
    assertThat(id).isEqualTo(99L);
    assertThat(saved.getValue().getEmployeeorderId()).isEqualTo(MY_ORDER);
    assertThat(saved.getValue().getTicketReferences()).containsExactly("ABC-1");
  }

  /** Otherwise the favourite would appear in the list of the other person. */
  @Test
  void a_favourite_on_the_employee_order_of_somebody_else_is_refused() {
    assertThatThrownBy(() -> favoriteService.addFavorite(new NewFavorite(FOREIGN_ORDER, 1, 0, null, null)))
        .isInstanceOf(ResponseStatusException.class);

    verify(favoriteRepository, never()).save(any());
  }

  @Test
  void an_unknown_employee_order_is_refused() {
    assertThatThrownBy(() -> favoriteService.addFavorite(new NewFavorite(404L, 1, 0, null, null)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void only_an_own_favourite_is_deleted() {
    when(favoriteRepository.findById(5L)).thenReturn(Optional.of(favoriteOn(MY_ORDER)));
    when(favoriteRepository.findById(6L)).thenReturn(Optional.of(favoriteOn(FOREIGN_ORDER)));

    favoriteService.deleteFavorite(5L);
    assertThatThrownBy(() -> favoriteService.deleteFavorite(6L)).isInstanceOf(ResponseStatusException.class);

    verify(favoriteRepository).deleteById(5L);
    verify(favoriteRepository, never()).deleteById(6L);
  }

  private static Favorite favoriteOn(long employeeorderId) {
    return Favorite.builder().employeeorder(employeeorderWithId(employeeorderId)).hours(1).minutes(0).build();
  }
}
