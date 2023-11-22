package de.hbt.salat.rest.favorites.adapter_rest;

import de.hbt.salat.rest.favorites.core.Favorite;
import java.util.Collection;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FavoriteDtoMapper {

    FavoriteDto map(Favorite favorite);
    Favorite map(FavoriteDto favorite);

    Collection<FavoriteDto> map(Collection<Favorite> favorites);


}
