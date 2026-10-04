package de.hbt.salat.auth.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "salat_user", uniqueConstraints = @UniqueConstraint(name = "uk_salat_user_loginname", columnNames = "loginname"))
public class SalatUser extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * login name of the user — unique (#1333): the sign-in looks up exactly one login per name
     */
    private String loginname;
    
    /**
     * status of the user (e.g., admin, ma, bl)
     */
    private String status;

    private transient Boolean restricted = null;

    @Transient
    public boolean isRestricted() {
        if (this.restricted == null) {
            this.restricted = GlobalConstants.EMPLOYEE_STATUS_RESTRICTED.equalsIgnoreCase(this.status);
        }
        return this.restricted;
    }

}
