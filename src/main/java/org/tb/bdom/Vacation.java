package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.*;
import java.io.Serializable;

/**
 * Bean for table 'vacation'
 *
 * @author oda
 */
@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Vacation implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employeecontract employeecontract;
    private Integer year;
    private Integer entitlement;
    private Integer used;

}
