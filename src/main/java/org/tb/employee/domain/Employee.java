package org.tb.employee.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.GlobalConstants;
import org.tb.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employee extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * registration first and last name of the employee
     */
    private String loginname;
    /**
     * first name of the employee
     */
    private String firstname;
    /**
     * last name of the employee
     */
    private String lastname;
    /**
     * sign of the employee (2 or 3 letters)
     */
    private String sign;
    /**
     * gender of the employee
     */
    private char gender;
    /**
     * status of the employee (e.g., admin, ma, bl
     */
    private String status;

    private transient Boolean restricted = null;

    public String getName() {
        return getFirstname() + " " + getLastname();
    }

    public String getEmailAddress() {
        return getSign() + "@" + GlobalConstants.MAIL_DOMAIN;
    }

    @Transient
    public boolean isRestricted() {
        if (this.restricted == null) {
            this.restricted = GlobalConstants.EMPLOYEE_STATUS_RESTRICTED.equalsIgnoreCase(this.status);
        }
        return this.restricted;
    }

}