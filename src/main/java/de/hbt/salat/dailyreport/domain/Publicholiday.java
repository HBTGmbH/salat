package de.hbt.salat.dailyreport.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_publicholiday_refdate", columnNames = "refdate"))
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Publicholiday extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** One row per day (#1208); the database says so as well. */
    private LocalDate refdate;
    private String name;

    public Publicholiday(LocalDate refdate, String name) {
        this.refdate = refdate;
        this.name = name;
    }

}
