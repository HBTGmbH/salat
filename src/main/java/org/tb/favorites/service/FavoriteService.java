package org.tb.favorites.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.favorites.domain.Favorite;
import org.tb.favorites.persistence.FavoriteRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final AuthorizedUser authorizedUser;

  public List<Favorite> getFavorites(Long employeeId) {
    return favoriteRepository.findAllByEmployeeId(employeeId);
  }

  public Optional<Favorite> getFavorite(Long favoriteId) {
    return favoriteRepository.findById(favoriteId);
  }

  public Favorite addFavorite(Favorite favorite) {
    favorite.setEmployeeId(authorizedUser.getEmployeeId());
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
      throw new IllegalArgumentException("Favorite not found for id " + id);
    }
    if (authorizedUser.getEmployeeId().equals(favorite.get().getEmployeeId())) {
      favoriteRepository.deleteById(id);
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your Favorite (id=" + id + ")");
    }
  }
}
