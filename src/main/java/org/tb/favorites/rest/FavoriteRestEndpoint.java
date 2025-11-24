package org.tb.favorites.rest;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.favorites.service.FavoriteService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/favorite", "/rest/favorite" })
@Tag(name = "favorite", description = "API zum verwalten von Favoriten für Zeitbuchungen")
public class FavoriteRestEndpoint {

  private final FavoriteService favoriteService;
  private final FavoriteDTOMapper mapper = Mappers.getMapper(FavoriteDTOMapper.class);
  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;

  @GetMapping(path = "", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public Collection<FavoriteDTO> getFavorites() {
    if(!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    return mapper.map(favoriteService.getFavorites(authorizedEmployee.getEmployeeId()));
  }

  @Operation(summary = "Fügt einen neuen Favoriten hinzu",
      description = "Speichert einen neuen Favoriten für den authentifizierten Benutzer")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "201", description = "Favorit erfolgreich gespeichert",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = FavoriteDTO.class))),
      @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = @Content)
  })
  @PutMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public FavoriteDTO addFavorite(@RequestBody @Parameter(description = "Der zu speichernde Favorit",
      required = true, schema = @Schema(implementation = FavoriteDTO.class)) FavoriteDTO favorite) {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    return mapper.map(favoriteService.addFavorite(mapper.map(favorite)));
  }

  @Operation(summary = "Löscht einen Favoriten",
      description = "Löscht einen bestehenden Favoriten anhand seiner ID")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Favorit erfolgreich gelöscht", 
          content = @Content),
      @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", 
          content = @Content),
      @ApiResponse(responseCode = "404", description = "Favorit nicht gefunden", 
          content = @Content)
  })
  @DeleteMapping(path = "/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void deleteFavorite(@PathVariable @Parameter(description = "ID des zu löschenden Favoriten", 
      required = true) long id) {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    favoriteService.deleteFavorite(id);
  }
}


