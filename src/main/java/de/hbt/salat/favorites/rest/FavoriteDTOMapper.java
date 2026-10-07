package de.hbt.salat.favorites.rest;

import java.util.List;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.favorites.domain.FavoriteEntry;
import de.hbt.salat.favorites.domain.NewFavorite;

@Mapper(collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FavoriteDTOMapper {

    /**
     * By hand: the single {@code ticketReference} of older clients is derived from the list (#1326).
     * The group (#1414) is read only — it is arranged in the application, not through the interface.
     */
    default FavoriteDTO map(FavoriteEntry favorite) {
        if (favorite == null) {
            return null;
        }
        var references = favorite.ticketReferences();
        return FavoriteDTO.builder()
            .id(favorite.id())
            .employeeorderId(favorite.employeeorderId())
            .hours(favorite.hours())
            .minutes(favorite.minutes())
            .comment(favorite.comment())
            .ticketReference(references.isEmpty() ? null : references.getFirst())
            .ticketReferences(references)
            .groupName(favorite.groupName())
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

    List<FavoriteDTO> map(List<FavoriteEntry> favorites);

}
