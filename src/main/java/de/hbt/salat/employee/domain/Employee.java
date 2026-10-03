package de.hbt.salat.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.common.Hiding;
import de.hbt.salat.common.domain.AuditedEntity;

import static de.hbt.salat.common.GlobalConstants.GENDER_MALE;

@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_employee_sign", columnNames = "sign"))
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
     * sign of the employee (2 or 3 letters) — required and unique (#1208); the database says so as well
     */
    @Column(nullable = false)
    private String sign;
    /**
     * gender of the employee
     */
    private char gender;

    @Getter(AccessLevel.NONE)
    private Boolean hide;

    /**
     * The SalatUser associated with this employee
     */
    @ManyToOne
    @JoinTable(
        name = "employee_salat_user",
        joinColumns = @JoinColumn(name = "employee_id", nullable = false),
        inverseJoinColumns = @JoinColumn(name = "salat_user_id", nullable = false),
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_salat_user", columnNames = {"employee_id", "salat_user_id"})
    )
    private SalatUser salatUser;

    /**
     * @return Returns true, if the {@link Employee} is hidden — {@code null} is not hidden
     *     (→ {@link Hiding}).
     */
    public Boolean getHide() {
        return Hiding.isHidden(hide);
    }

    public String getName() {
        return getFirstname() + " " + getLastname();
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

    public boolean isMale() {
        return Objects.equals(gender, GENDER_MALE);
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