package de.hbt.salat.rest.favorites.adapter_rest;

import de.hbt.salat.rest.favorites.core.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/rest/favorite")
@SecurityRequirement(name = "bearerAuth")
public class FavoriteRestEndpoint {

  private final FavoriteService favoriteService;
  private final FavoriteDtoMapper favoriteDtoMapper = Mappers.getMapper(FavoriteDtoMapper.class);

  @GetMapping(path = "", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  @Operation(security = {@SecurityRequirement(name = "bearerAuth")})
  public Collection<FavoriteDto> getFavorites() {
    return favoriteDtoMapper.map(favoriteService.getFavorites());
  }
}


