package de.hbt.salat.dailyreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.domain.DurationMinutesConverter;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * Einen Arbeitstag gibt es je Mitarbeitervertrag und Tag genau einmal. Der Unique Key dazu steht
 * seit jeher in der Datenbank, aber nicht im Mapping — und damit auch nicht in dem Schema, das die
 * Tests daraus erzeugen. Ohne ihn ließe sich das gleichzeitige Anlegen desselben Arbeitstags nicht
 * nachstellen, obwohl genau daran eine Anfrage scheitert (#1111). Die Schemaprüfung beim Start
 * vergleicht Tabellen, Spalten und Typen, keine Schlüssel; die Angabe wirkt deshalb allein auf das
 * erzeugte Schema.
 */
@Getter
@Setter
@Entity
@Table(name = "workingday", uniqueConstraints = @UniqueConstraint(
    name = "workingday_uk1", columnNames = {"EMPLOYEECONTRACT_ID", "refday"}))
public class Workingday extends AuditedEntity implements Serializable {

    public enum WorkingDayType { WORKED, NOT_WORKED }

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    private Employeecontract employeecontract;

    private LocalDate refday;

    /**
     * The start of the working day (#1382): one {@code TIME} column instead of hour and minute,
     * which had no meaning of their own. A time of day rather than minutes since midnight, so that
     * the column reads as what it is in a report as well.
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime = LocalTime.MIDNIGHT;

    /** The length of the break (#1382), stored in minutes in one column. */
    @Convert(converter = DurationMinutesConverter.class)
    @Column(name = "break_minutes", nullable = false)
    private Duration breakLength = Duration.ZERO;

    @Enumerated(EnumType.STRING)
    private WorkingDayType type = WorkingDayType.WORKED;

    public LocalDateTime getStartOfWorkingDay() {
        return LocalDateTime.of(refday, startTime);
    }

    public long getBreakLengthInMinutes() {
        return breakLength.toMinutes();
    }
}
