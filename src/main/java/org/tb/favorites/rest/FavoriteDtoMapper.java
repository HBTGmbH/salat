package org.tb.favorites.rest;

import java.util.List;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.tb.favorites.domain.Favorite;

@Mapper(collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FavoriteDtoMapper {

    FavoriteDto map(Favorite favorite);
    Favorite map(FavoriteDto favorite);
    List<FavoriteDto> map(List<Favorite> favorites);

}
