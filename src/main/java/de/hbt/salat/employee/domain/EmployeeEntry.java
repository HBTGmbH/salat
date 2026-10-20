package de.hbt.salat.employee.domain;

import de.hbt.salat.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * The day a person joined the company where it lies before their first contract in Salat (#1501):
 * Salat started in 2007, and whoever joined earlier carries 01.01.2007 as the start of the first
 * contract. {@link Tenure} counts from this day instead.
 *
 * <p>Maintained by hand with SQL, without a page; the application only reads it. A row needs no more
 * than {@code employee_id} and {@code entry_date}.
 */
@Entity
@Immutable
@Table(name = "employee_entry", uniqueConstraints = @UniqueConstraint(name = "uk_employee_entry_employee_id",
    columnNames = "employee_id"))
@Getter
@NoArgsConstructor
public class EmployeeEntry extends AuditedEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "fk_employee_entry_employee"))
  private Employee employee;

  @Column(name = "entry_date", nullable = false)
  private LocalDate entryDate;
}
