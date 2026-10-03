package de.hbt.salat.dailyreport.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.util.DateUtils;

/**
 * The day every booking of a date shares.
 *
 * <p>{@code dow}, {@code holiday}, {@code name} and {@code workingday} are <em>derived</em> (#1211): from the date
 * and from {@link Publicholiday}, the one source of public holidays. They are set only through
 * {@link #applyCalendar} — on creation, and again whenever the holidays of that date change. The application reads
 * none of them; weekends, holidays and the working time target come from the date and {@code publicholiday}
 * directly. The columns stay because stored reports read them.
 *
 * <ul>
 *   <li>{@code holiday}: the date has an entry in {@code publicholiday}. A weekend is no holiday, it is no working
 *       day. Up to 2024 the stock had set {@code holiday} on weekends as well; changeset 115 aligned it.</li>
 *   <li>{@code name}: the name of that entry, otherwise empty.</li>
 *   <li>{@code workingday}: a weekday that is no holiday.</li>
 *   <li>{@code dow}: the English short name of the weekday ({@code Mon} … {@code Sun}).</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_referenceday_refdate", columnNames = "refdate"))
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Referenceday extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** One row per day (#1208); the database says so as well. */
    private LocalDate refdate;

    @Setter(AccessLevel.NONE)
    private Boolean workingday;
    @Setter(AccessLevel.NONE)
    private String dow;
    @Setter(AccessLevel.NONE)
    private Boolean holiday;
    @Setter(AccessLevel.NONE)
    private String name;

    /**
     * Derives the calendar columns from the date and the public holiday on it.
     *
     * @param holidayName the name of the entry in {@code publicholiday} on this date, {@code null} if there is none
     */
    public void applyCalendar(@Nullable String holidayName) {
        holiday = holidayName != null;
        name = holidayName != null ? holidayName : "";
        workingday = !holiday && DateUtils.isWeekday(refdate);
        dow = DateUtils.getDoW(refdate);
    }

}
