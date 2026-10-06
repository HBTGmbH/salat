package de.hbt.salat.favorites.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.util.TicketReferences;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.favorites.domain.Favorite;
import de.hbt.salat.favorites.domain.NewFavorite;
import de.hbt.salat.favorites.persistence.EmployeeorderReferences;
import de.hbt.salat.favorites.persistence.FavoriteRepository;
import de.hbt.salat.order.service.EmployeeorderService;

@Slf4j
@Service
@RequiredArgsConstructor
@Authorized
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final AuthorizedEmployee authorizedEmployee;
  private final EmployeeorderReferences employeeorderReferences;
  private final EmployeeorderService employeeorderService;

  public List<Favorite> getFavorites(long employeeId) {
    return favoriteRepository.findAllByEmployeeId(employeeId);
  }

  public Optional<Favorite> getFavorite(long favoriteId) {
    return favoriteRepository.findById(favoriteId);
  }

  /**
   * Adds a favourite and stores its ticket references under the rule a booking stores them under
   * (#1326).
   *
   * <p>The person of a favourite is the person of its employee order (#1369). An employee order of
   * somebody else would put the favourite into that person's list, so it is refused.
   *
   * @return the id of the new favourite
   */
  public long addFavorite(NewFavorite newFavorite) {
    if (!isOwnEmployeeorder(newFavorite.employeeorderId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "not your employee order (id=" + newFavorite.employeeorderId() + ")");
    }
    var favorite = Favorite.builder()
        .employeeorder(employeeorderReferences.employeeorder(newFavorite.employeeorderId()))
        .hours(newFavorite.hours())
        .minutes(newFavorite.minutes())
        .comment(newFavorite.comment())
        .ticketReferences(new ArrayList<>(TicketReferences.normalize(newFavorite.ticketReferences())))
        .build();
    try {
      return favoriteRepository.save(favorite).getId();
    } catch (DataIntegrityViolationException e) {
      log.error("Could not save {}.", favorite, e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * Whether a favourite on this employee order is the login's own (#1369). The favourite has no
   * person of its own; whose the employee order is, the order module answers.
   */
  public boolean isOwnEmployeeorder(long employeeorderId) {
    return employeeorderService.getEmployeeIdOfEmployeeorder(employeeorderId)
        .filter(authorizedEmployee.getEmployeeId()::equals)
        .isPresent();
  }

  public void deleteFavorite(long id) {
    var favorite = favoriteRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite not found for id " + id));
    if (isOwnEmployeeorder(favorite.getEmployeeorderId())) {
      favoriteRepository.deleteById(id);
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your Favorite (id=" + id + ")");
    }
  }
}
