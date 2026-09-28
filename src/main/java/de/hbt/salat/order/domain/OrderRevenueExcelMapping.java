package de.hbt.salat.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.employee.domain.Employee;

@Getter
@Setter
@Entity
@Table(name = "excel_import_mapping")
public class OrderRevenueExcelMapping extends AuditedEntity {

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "source_column", nullable = false)
    private String sourceColumn;

    @Column(name = "target_field", nullable = false)
    private String targetField;

}
