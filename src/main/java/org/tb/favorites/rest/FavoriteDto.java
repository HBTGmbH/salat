package org.tb.favorites.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Schema(description = "to save your favorite reports", name = "Favorite")
public class FavoriteDto implements Serializable {
  private Long id;
  private Long employeeorderId;
  private int hours;
  private int minutes;
  private String comment;
}
