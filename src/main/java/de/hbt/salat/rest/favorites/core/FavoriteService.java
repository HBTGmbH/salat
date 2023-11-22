package de.hbt.salat.rest.favorites.core;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FavoriteService {
  private final FavoriteRepository favoriteRepository;

  public Collection<Favorite> getFavorites(Long employeeId) {
    return favoriteRepository.findAllByEmployeeId(employeeId);
  }
}
