package de.hbt.salat.rest.favorites.core;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;
  Long employeeId;
  Long employeeorderId;
  long hours;
  long minutes;
  String comment;
}
