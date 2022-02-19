package org.tb.bdom;

import java.io.Serializable;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Customer extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String shortname;
    private String address;

    /**
     * TODO check removal
     * list of customerorders, associated to this customer
     */
    @OneToMany(mappedBy = "customer")
    @Cascade(CascadeType.SAVE_UPDATE)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Customerorder> customerorders;

    /**
     * TODO check removal
     * list of invoices, associated to this customer
     */
    @OneToMany(mappedBy = "customer")
    @Cascade(CascadeType.SAVE_UPDATE)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Invoice> invoices;

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
