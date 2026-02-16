package org.tb.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.domain.AuditedEntity;
import org.tb.employee.domain.Employee;

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
