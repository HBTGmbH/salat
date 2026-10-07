package de.hbt.salat.favorites.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.employee.domain.Employee;

/**
 * A group a person sorts their favourites into (#1414), named by them. It belongs to the person and
 * nobody else sees or changes it; the favourites module checks that on every access.
 *
 * <p>The person is master data of the employee module (ADR-0036): a read-only reference, never
 * cascaded. Deleting a group leaves its favourites in place, without a group.
 */
@Entity
@Table(name = "favorite_group", uniqueConstraints = @UniqueConstraint(name = "uk_favorite_group_employee_id_name",
    columnNames = {"employee_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class FavoriteGroup extends AuditedEntity {

  public static final int NAME_MAX_LENGTH = 64;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "fk_favorite_group_employee"))
  private Employee employee;

  @Column(nullable = false, length = NAME_MAX_LENGTH)
  private String name;

  /** The place among the groups of the person, from the top. */
  @Column(nullable = false)
  private int position;

  /** The id of the person; reading it does not load the employee. */
  public Long getEmployeeId() {
    return employee == null ? null : employee.getId();
  }
}
