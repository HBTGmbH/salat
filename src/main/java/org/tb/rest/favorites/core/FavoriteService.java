package org.tb.rest.favorites.core;

import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.webjars.NotFoundException;

@Service
@RequiredArgsConstructor
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final AuthorizedUser authorizedUser;

  public Collection<Favorite> getFavorites(Long employeeId) {
    return favoriteRepository.findAllByEmployeeId(employeeId);
  }

  public Favorite addFavorite(Favorite favorite) {
    favorite.setEmployeeId(authorizedUser.getEmployeeId());
    try {
      return favoriteRepository.save(favorite);
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  public void deleteFavorite(long id) {
    Optional<Favorite> favorite = favoriteRepository.findById(id);
    if (favorite.isEmpty()) {
      throw new NotFoundException("Favorite not found");
    }
    if (authorizedUser.getEmployeeId().equals(favorite.get().getEmployeeId())) {

      favoriteRepository.deleteById(id);
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your Favorite");
    }
  }
}
