package org.tb.bdom;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Transient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.GlobalConstants;
import org.tb.util.SecureHashUtils;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employee implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;
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
     * Creation Date
     */
    private java.util.Date created;
    /**
     * Last Update
     */
    private java.util.Date lastupdate;
    /**
     * Created By
     */
    private String createdby;
    /**
     * Updated By
     */
    private String lastupdatedby;
    /**
     * Update Counter
     */
    private Integer updatecounter;
    /**
     * Password change required
     */
    private Boolean passwordchange;
    /**
     * Jira OAuth Token
     */
    private String jira_oauthtoken;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        Employee employee = (Employee) o;
        return Objects.equals(id, employee.id) && sign != null && Objects.equals(sign, employee.sign);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id) + sign.hashCode();
    }
}
