package org.tb.customer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import java.io.Serial;
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

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String shortname;
    private String address;
    private Boolean hide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinTable(
        name = "customer_segment_customer",
        joinColumns = @JoinColumn(name = "customer_id"),
        inverseJoinColumns = @JoinColumn(name = "customer_segment_id")
    )
    private CustomerSegment segment;

    public Boolean getHide() {
        if (hide == null) {
            return false;
        }
        return hide;
    }

    public String getShortname() {
        if (shortname == null || shortname.isEmpty()) {
            if (name != null && name.length() > 12) {
                return name.substring(0, 9) + "...";
            } else {
                return name;
            }
        }
        return shortname;
    }

}
