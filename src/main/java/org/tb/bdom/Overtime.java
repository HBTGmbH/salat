package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;
import org.tb.GlobalConstants;

import javax.persistence.Entity;
import javax.persistence.*;
import java.io.Serializable;
import java.text.SimpleDateFormat;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Overtime extends EditDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employeecontract employeecontract;
    private String comment;
    private Double time;

}
