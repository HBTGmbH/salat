package de.hbt.salat.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;

/**
 * A cost category of the internal hourly costs (#1209): what rate periods ({@link EmployeeCost})
 * and assignments ({@link EmployeeCostAssignment}) refer to by foreign key.
 *
 * <p>Until #1209 a category was only a name that several rate periods and every assignment carried.
 * Renaming it had to hit all of them, and a typo in the name silently made a new category. Now the
 * name sits here, unique, and a rename changes this one row. The name columns of the two tables are
 * gone (#1345).
 *
 * <p>The name compares as stored, case included, like the name columns it replaces
 * ({@code utf8mb3_bin}) and like the lookup in memory did.
 */
@Entity
@Table(name = "employee_cost_category",
    uniqueConstraints = @UniqueConstraint(name = "uk_employee_cost_category_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CostCategory extends AuditedEntity {

    @Column(nullable = false)
    private String name;

    public CostCategory(String name) {
        this.name = name;
    }

}
