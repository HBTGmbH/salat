package org.tb.bdom;

import java.io.Serializable;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
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
public class Customer extends EditDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id = -1;
    private String name;
    private String shortname;
    private String address;

    /**
     * list of customerorders, associated to this customer
     */
    @OneToMany(mappedBy = "customer")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Customerorder> customerorders;

    /**
     * list of invoices, associated to this customer
     */
    @OneToMany(mappedBy = "customer")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
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
