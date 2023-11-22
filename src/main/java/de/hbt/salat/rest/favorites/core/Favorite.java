package de.hbt.salat.rest.favorites.core;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Favorite {
  Long id;
  Long employeeorderId;
  long hours;
  long minutes;
  String comment;
}
