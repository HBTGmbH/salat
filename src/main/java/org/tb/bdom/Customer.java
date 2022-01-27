package org.tb.bdom;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.io.Serializable;
import java.util.List;

@Data
@Entity
@EqualsAndHashCode(of = "name", callSuper = false)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@ToString(exclude = { "customerorders", "invoices" })
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
