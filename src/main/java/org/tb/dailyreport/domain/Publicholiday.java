package org.tb.dailyreport.domain;

import jakarta.persistence.Entity;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Publicholiday extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDate refdate;
    private String name;

    public Publicholiday(LocalDate refdate, String name) {
        this.refdate = refdate;
        this.name = name;
    }

}
