package org.tb.bdom;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.*;
import java.io.Serializable;

/**
 * Bean for table 'invoice'.
 *
 * @author oda
 */
@Data
@Entity
@EqualsAndHashCode(of = "description")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Invoice implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMER_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Customer customer;
    private String description;
    private java.util.Date created;
    private java.util.Date lastupdate;
    private String createdby;
    private String lastupdatedby;
    private Integer updatecounter;

}
