package org.tb.customer.domain;

import jakarta.persistence.Entity;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Customer extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String shortname;
    private String address;

    public String getShortname() {
        if (shortname == null || shortname.equals("")) {
            if (name.length() > 12) {
                return name.substring(0, 9) + "...";
            } else {
                return name;
            }
        }
        return shortname;
    }

}
