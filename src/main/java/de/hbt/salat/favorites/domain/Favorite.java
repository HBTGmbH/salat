package de.hbt.salat.favorites.domain;

import static lombok.AccessLevel.PRIVATE;
import static de.hbt.salat.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.domain.Persistable;
import de.hbt.salat.order.domain.Employeeorder;

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

  /**
   * The employee order the favourite books on — and through it the person it belongs to (#1369).
   * A reference to master data of order (ADR-0036): read only, never cascaded.
   */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employeeorderId", nullable = false)
  private Employeeorder employeeorder;

  @Column(nullable = false)
  private Integer hours;

  @Column(nullable = false)
  private Integer minutes;

  @Lob
  @Column(columnDefinition = "text")
  private String comment;

  /**
   * The ticket references of the booking this favourite was made from (#1029, #1326), in their order.
   * Applying the favourite writes them back; whether the suborder still allows that many is checked
   * when the booking is saved, and a refusal is reported rather than cutting the list short.
   */
  @ElementCollection
  @CollectionTable(name = "favorite_ticket_reference",
      joinColumns = @JoinColumn(name = "favorite_id",
          foreignKey = @ForeignKey(name = "fk_favorite_ticket_reference_favorite")),
      uniqueConstraints = @UniqueConstraint(name = "uk_favorite_ticket_reference_favorite_id_reference",
          columnNames = {"favorite_id", "reference"}))
  @OrderColumn(name = "position", nullable = false)
  @ListIndexBase(1)
  @Column(name = "reference", nullable = false, length = TICKET_REFERENCE_MAX_LENGTH)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @Builder.Default
  private List<String> ticketReferences = new ArrayList<>();

  /** The id of the employee order; reading it does not load the employee order. */
  public Long getEmployeeorderId() {
    return employeeorder == null ? null : employeeorder.getId();
  }

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
