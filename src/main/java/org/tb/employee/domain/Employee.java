package org.tb.employee.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.auth.domain.SalatUser;
import org.tb.common.GlobalConstants;
import org.tb.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employee extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * The SalatUser associated with this employee
     */
    @ManyToOne
    @JoinTable(
        name = "employee_salat_user",
        joinColumns = @JoinColumn(name = "employee_id", nullable = false),
        inverseJoinColumns = @JoinColumn(name = "salat_user_id", nullable = false)
    )
    private SalatUser salatUser;

    public String getName() {
        return getFirstname() + " " + getLastname();
    }

    public String getEmailAddress() {
        return getSign() + "@" + GlobalConstants.MAIL_DOMAIN;
    }

    @Transient
    public String getLoginname() {
        return salatUser != null ? salatUser.getLoginname() : null;
    }

    @Transient
    public void setLoginname(String loginname) {
        if (salatUser != null) {
            salatUser.setLoginname(loginname);
        }
    }

    @Transient
    public String getStatus() {
        return salatUser != null ? salatUser.getStatus() : null;
    }

    @Transient
    public void setStatus(String status) {
        if (salatUser != null) {
            salatUser.setStatus(status);
        }
    }

    @Transient
    public boolean isRestricted() {
        return salatUser != null && salatUser.isRestricted();
    }

}