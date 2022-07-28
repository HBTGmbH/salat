package org.tb.auth;

import lombok.Getter;
import lombok.Setter;
import org.tb.common.AuditedEntity;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;

import javax.persistence.*;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "employee_to_employee_authorization_rule")
public class EmployeeToEmployeeAuthorizationRule extends AuditedEntity {

    @ManyToOne
    @JoinColumn(name = "grantor_id")
    private Employee grantor;

    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private Employee recipient;

    @ManyToOne
    @JoinColumn(name = "customer_order_id")
    private Customerorder customerOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level")
    private AccessLevel accessLevel;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

}
