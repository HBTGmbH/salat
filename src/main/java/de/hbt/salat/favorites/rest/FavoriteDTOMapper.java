package de.hbt.salat.favorites.rest;

import java.util.List;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.favorites.domain.Favorite;
import de.hbt.salat.favorites.domain.NewFavorite;

@Mapper(collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FavoriteDTOMapper {

    /** By hand: the single {@code ticketReference} of older clients is derived from the list (#1326). */
    default FavoriteDTO map(Favorite favorite) {
        if (favorite == null) {
            return null;
        }
        var references = List.copyOf(favorite.getTicketReferences());
        return FavoriteDTO.builder()
            .id(favorite.getId())
            .employeeorderId(favorite.getEmployeeorderId())
            .hours(favorite.getHours() == null ? 0 : favorite.getHours())
            .minutes(favorite.getMinutes() == null ? 0 : favorite.getMinutes())
            .comment(favorite.getComment())
            .ticketReference(references.isEmpty() ? null : references.getFirst())
            .ticketReferences(references)
            .build();
    }

    /**
     * By hand: without a list, the single {@code ticketReference} of an older client is the only one.
     * An id in the request is not taken over — a favourite is always added (#1369).
     */
    default NewFavorite map(FavoriteDTO favorite) {
        if (favorite == null) {
            return null;
        }
        if (favorite.getEmployeeorderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "employeeorderId is required");
        }
        List<String> references = favorite.getTicketReferences() != null ? favorite.getTicketReferences()
            : favorite.getTicketReference() != null ? List.of(favorite.getTicketReference())
            : List.of();
        return new NewFavorite(favorite.getEmployeeorderId(), favorite.getHours(), favorite.getMinutes(),
            favorite.getComment(), references);
    }

    List<FavoriteDTO> map(List<Favorite> favorites);

}
