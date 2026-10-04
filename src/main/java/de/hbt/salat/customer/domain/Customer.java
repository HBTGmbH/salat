package de.hbt.salat.customer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.Hiding;
import de.hbt.salat.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_customer_shortname", columnNames = "shortname"))
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Customer extends AuditedEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    /** Unique (#1333): the short name is how a customer is told apart in every select (#1266). */
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

    /**
     * @return Returns true, if the {@link Customer} is hidden — {@code null} is not hidden
     *     (→ {@link Hiding}).
     */
    public Boolean getHide() {
        return Hiding.isHidden(hide);
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
