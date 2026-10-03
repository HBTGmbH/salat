package de.hbt.salat.order.domain;

import de.hbt.salat.common.Hiding;
import de.hbt.salat.common.Validity;
import static de.hbt.salat.common.util.DateUtils.format;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.domain.DurationMinutesConverter;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.order.domain.comparator.SubOrderComparator;

/**
 * Bean for table 'customerorder'.
 *
 * @author oda
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_customerorder_sign", columnNames = "sign"))
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Customerorder extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMER_ID")
    private Customer customer;

    /**
     * list of suborders, associated to this customerorder
     */
    @OneToMany(mappedBy = "customerorder")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Suborder> suborders;

    /**
     * Responsible of Customer
     */
    private String responsible_customer_technical;
    private String responsible_customer_contractually;

    /**
     * Responsible employees of HBT
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "customerorder_responsible_hbt",
        joinColumns = @JoinColumn(name = "CUSTOMERORDER_ID"),
        inverseJoinColumns = @JoinColumn(name = "EMPLOYEE_ID"),
        uniqueConstraints = @UniqueConstraint(name = "uk_customerorder_responsible_hbt",
            columnNames = {"CUSTOMERORDER_ID", "EMPLOYEE_ID"}))
    @Fetch(FetchMode.SELECT)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Employee> responsibleHbt = new ArrayList<>();

    /**
     * contractually responsible employee of HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "RESPONSIBLE_HBT_CONTRACTUALLY_ID")
    private Employee respEmpHbtContract;

    /**
     * Orderer of Customer
     */
    private String order_customer;
    private LocalDate fromDate;
    private LocalDate untilDate;
    /** Required and unique (#1208); the database says so as well. */
    @Column(nullable = false)
    private String sign;
    private String description;
    private String shortdescription;

    @Convert(converter = DurationMinutesConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private Duration debitMinutes;

    private Byte debithoursunit;
    /**
     * Hide in select boxes
     */
    private Boolean hide;

    /**
     * May be overridden by suborder ordertype!
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "orderType", columnDefinition = "varchar(255)")
    private OrderType orderType;

    public String getFormattedUntilDate() {
        LocalDate untilLocalDate = getUntilDate();
        if (untilLocalDate != null) {
            return format(untilDate);
        }
        return "";
    }

    public List<Suborder> getSuborders() {
        suborders.sort(SubOrderComparator.INSTANCE);
        return suborders;
    }

    public String getShortdescription() {
        if ((shortdescription == null || shortdescription.isEmpty()) && description == null) {
            description = "";
        }
        return shortdescriptionOf(shortdescription, description);
    }

    /**
     * {@link #getShortdescription()} for code that has the two values but not the order
     * ({@link CustomerorderOption}, #1283): the short description, or else the description, cut to
     * twenty characters.
     */
    public static String shortdescriptionOf(String shortdescription, String description) {
        if (shortdescription == null || shortdescription.isEmpty()) {
            if (description == null) {
                return "";
            }
            return description.length() > 20 ? description.substring(0, 17) + "..." : description;
        }
        return shortdescription;
    }

    public String getSignAndDescription() {
        return sign + " - " + getShortdescription() + " (" + customer.getShortname() + ")";
    }

    /**
     * @return Returns true, if the {@link Customerorder} is hidden — {@code null} is not hidden
     *     (→ {@link Hiding}).
     */
    public Boolean getHide() {
        return Hiding.isHidden(hide);
    }

    /**
     * @return Returns true, if the {@link Customerorder} is not inactive, i.e. its validity has not
     *     ended before today (→ {@link Validity}).
     */
    public boolean getCurrentlyValid() {
        return !Validity.isInactive(untilDate);
    }

    public boolean isValidAt(LocalDate date) {
        return !date.isBefore(getFromDate()) && (getUntilDate() == null || !date.isAfter(getUntilDate()));
    }

    public Duration getDebithours() {
        return debitMinutes; // its a Duration - hours or minutes make no difference
    }

    public void setDebithours(Duration value) {
        debitMinutes = value; // its a Duration - hours or minutes make no difference
    }

    public LocalDateRange getValidity() {
        return new LocalDateRange(getFromDate(), getUntilDate());
    }
}
