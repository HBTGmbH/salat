package de.hbt.salat.rest.favorites.adapter_rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Schema(description = "to save your favorite reports", name = "Favorite")
public class FavoriteDto {
  Long id;
  Long employeeorderId;
  long hours;
  long minutes;
  String comment;
}
