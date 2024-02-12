package org.tb.favorites.rest;

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
  private Long id;
  private Long employeeorderId;
  private int hours;
  private int minutes;
  private String comment;
}
