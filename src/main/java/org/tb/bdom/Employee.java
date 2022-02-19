package org.tb.bdom;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.GlobalConstants;
import org.tb.util.SecureHashUtils;

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
     * registration password of the employee
     */
    private String password;
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
    /**
     * Password change required
     */
    private Boolean passwordchange;

    private transient Boolean restricted = null;

    public String getName() {
        return getFirstname() + " " + getLastname();
    }

    public String getEmailAddress() {
        return getSign() + "@" + GlobalConstants.MAIL_DOMAIN;
    }

    public void resetPassword() {
        password = SecureHashUtils.encodePassword(sign);
        passwordchange = true;
    }

    public void changePassword(final String newPassword) {
        passwordchange = false;
        password = SecureHashUtils.encodePassword(newPassword);
    }

    @Transient
    public boolean isRestricted() {
        if (this.restricted == null) {
            this.restricted = GlobalConstants.EMPLOYEE_STATUS_RESTRICTED.equalsIgnoreCase(this.status);
        }
        return this.restricted;
    }

}
