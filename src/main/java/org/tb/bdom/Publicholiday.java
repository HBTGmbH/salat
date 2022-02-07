package org.tb.bdom;

import static javax.persistence.TemporalType.DATE;

import java.util.Date;
import javax.persistence.Temporal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.io.Serializable;

@Data
@Entity
@EqualsAndHashCode(of = "refdate")
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Publicholiday implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @Temporal(DATE)
    private Date refdate;
    private String name;

    public Publicholiday(Date refdate, String name) {
        this.refdate = refdate;
        this.name = name;
    }

}
