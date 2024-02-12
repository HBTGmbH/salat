package org.tb.rest.favorites.adapter_rest;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.rest.favorites.core.FavoriteService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/rest/favorite")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "favorite")
public class FavoriteRestEndpoint {

  private final FavoriteService favoriteService;
  private final FavoriteDtoMapper favoriteDtoMapper = Mappers.getMapper(FavoriteDtoMapper.class);
  private final AuthorizedUser authorizedUser;

  @GetMapping(path = "", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  @Operation(security = {@SecurityRequirement(name = "bearerAuth")})
  public Collection<FavoriteDto> getFavorites() {
    if(!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    return favoriteDtoMapper.map(favoriteService.getFavorites(authorizedUser.getEmployeeId()));
  }

  @PutMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(security = {@SecurityRequirement(name = "bearerAuth")})
  public FavoriteDto addFavorite(@RequestBody FavoriteDto favorite) {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    return favoriteDtoMapper.map(favoriteService.addFavorite(favoriteDtoMapper.map(favorite)));
  }

  @DeleteMapping(path = "/{id}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(security = {@SecurityRequirement(name = "bearerAuth")})
  public void deleteFavorite(@PathVariable long id) {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    favoriteService.deleteFavorite(id);
  }
}


