package de.hbt.salat.favorites.rest;

import java.util.ArrayList;
import java.util.List;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import de.hbt.salat.favorites.domain.Favorite;

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

    /** By hand: without a list, the single {@code ticketReference} of an older client is the only one. */
    default Favorite map(FavoriteDTO favorite) {
        if (favorite == null) {
            return null;
        }
        List<String> references = favorite.getTicketReferences() != null ? favorite.getTicketReferences()
            : favorite.getTicketReference() != null ? List.of(favorite.getTicketReference())
            : List.of();
        return Favorite.builder()
            .employeeorderId(favorite.getEmployeeorderId())
            .hours(favorite.getHours())
            .minutes(favorite.getMinutes())
            .comment(favorite.getComment())
            .ticketReferences(new ArrayList<>(references))
            .build();
    }

    List<FavoriteDTO> map(List<Favorite> favorites);

}
