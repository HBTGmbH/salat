package org.tb.favorites.domain;

import static lombok.AccessLevel.PRIVATE;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Builder
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Favorite implements Persistable<Long> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(PRIVATE)
  private Long id;

  @Column(nullable = false)
  private Long employeeId;

  @Column(nullable = false)
  private Long employeeorderId;

  @Column(nullable = false)
  private Integer hours;

  @Column(nullable = false)
  private Integer minutes;

  @Lob
  @Column(columnDefinition = "text")
  private String comment;

  @Override
  public boolean isNew() {
    return id == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if(id == null) return false;
    Favorite that = (Favorite) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    if(id == null) return 0;
    return Objects.hash(id);
  }

}
