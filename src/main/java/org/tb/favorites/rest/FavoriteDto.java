package org.tb.favorites.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "to save your favorite reports", name = "Favorite")
public class FavoriteDto {
  private Long id;
  private Long employeeorderId;
  private int hours;
  private int minutes;
  private String comment;
}
