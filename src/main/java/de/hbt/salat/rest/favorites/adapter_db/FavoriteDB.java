package de.hbt.salat.rest.favorites.adapter_db;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class FavoriteDB {
  Long id;
  Long employeeorderId;
  long hours;
  long minutes;
  String comment;
}
