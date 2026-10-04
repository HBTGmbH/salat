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
import de.hbt.salat.favorites.persistence.FavoriteRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Authorized
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final AuthorizedEmployee authorizedEmployee;

  public List<Favorite> getFavorites(long employeeId) {
    return favoriteRepository.findAllByEmployeeId(employeeId);
  }

  public Optional<Favorite> getFavorite(long favoriteId) {
    return favoriteRepository.findById(favoriteId);
  }

  /** Stores the ticket references under the rule a booking stores them under (#1326). */
  public Favorite addFavorite(Favorite favorite) {
    favorite.setEmployeeId(authorizedEmployee.getEmployeeId());
    favorite.setTicketReferences(new ArrayList<>(TicketReferences.normalize(favorite.getTicketReferences())));
    try {
      return favoriteRepository.save(favorite);
    } catch (DataIntegrityViolationException e) {
      log.error("Could not save {}.", favorite, e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  public void deleteFavorite(long id) {
    Optional<Favorite> favorite = favoriteRepository.findById(id);
    if (favorite.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite not found for id " + id);
    }
    if (authorizedEmployee.getEmployeeId().equals(favorite.get().getEmployeeId())) {
      favoriteRepository.deleteById(id);
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your Favorite (id=" + id + ")");
    }
  }
}
