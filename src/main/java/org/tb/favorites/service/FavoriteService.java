package org.tb.favorites.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.employee.service.EmployeeService;
import org.tb.favorites.domain.Favorite;
import org.tb.favorites.persistence.FavoriteRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Authorized
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final AuthorizedUser authorizedUser;
  private final EmployeeService employeeService;

  public List<Favorite> getFavorites(long employeeId) {
    return favoriteRepository.findAllByEmployeeId(employeeId);
  }

  public Optional<Favorite> getFavorite(long favoriteId) {
    return favoriteRepository.findById(favoriteId);
  }

  public Favorite addFavorite(Favorite favorite) {
    var loginEmployee = employeeService.getLoginEmployee();
    favorite.setEmployeeId(loginEmployee.getId());
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
    var loginEmployee = employeeService.getLoginEmployee();
    if (loginEmployee.getId().equals(favorite.get().getEmployeeId())) {
      favoriteRepository.deleteById(id);
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your Favorite (id=" + id + ")");
    }
  }
}
