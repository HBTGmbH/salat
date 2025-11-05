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
public class SalatUser extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * registration first and last name of the user
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
