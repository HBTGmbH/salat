package org.tb.favorites.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Builder
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Favorite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;
  Long employeeId;
  Long employeeorderId;
  Integer hours;
  Integer minutes;
  @Lob
  @Column(columnDefinition = "text")
  String comment;
}
