package org.tb.favorites.domain;

import static lombok.AccessLevel.PRIVATE;
import static org.tb.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;

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

  /**
   * The ticket reference of the booking this favourite was made from (#1029). Applying the
   * favourite writes it back; a favourite from before this existed has none and produces a booking
   * without one.
   */
  // explicit name: the physical naming strategy keeps the attribute name as it is, and the column
  // is spelled the way it is on timereport
  @Column(name = "ticket_reference", length = TICKET_REFERENCE_MAX_LENGTH)
  private String ticketReference;

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
