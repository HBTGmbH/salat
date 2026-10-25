package de.hbt.salat.order.domain;

import de.hbt.salat.common.Hiding;
import de.hbt.salat.common.Validity;
import static de.hbt.salat.common.util.DateUtils.format;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
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
import de.hbt.salat.tag.domain.Tag;

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
     * The customer who placed the order on behalf of {@link #customer} (#1536), {@code null} for an order placed
     * directly. Master data of the module {@code customer}, referenced read only (ADR-0036); the invoice attachment goes
     * to it. A list reads it with the order rather than one statement per row.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intermediary_customer_id")
    private Customer intermediary;

    /**
     * list of suborders, associated to this customerorder
     */
    @OneToMany(mappedBy = "customerorder")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Getter(AccessLevel.NONE)
    private List<Suborder> suborders;

    /**
     * Responsible of Customer
     */
    private String responsible_customer_technical;
    private String responsible_customer_contractually;

    /**
     * Responsible employees of HBT. Read with every order, and a list of orders reads them for many orders at once
     * rather than one statement per order (#1380).
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "customerorder_responsible_hbt",
        joinColumns = @JoinColumn(name = "CUSTOMERORDER_ID"),
        inverseJoinColumns = @JoinColumn(name = "EMPLOYEE_ID"),
        uniqueConstraints = @UniqueConstraint(name = "uk_customerorder_responsible_hbt",
            columnNames = {"CUSTOMERORDER_ID", "EMPLOYEE_ID"}))
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Getter(AccessLevel.NONE)
    private List<Employee> responsibleHbt = new ArrayList<>();

    /**
     * The tags of the order (#847, → ADR-0042): master data of the module {@code tag}, referenced over the join table
     * {@code customerorder_tag}. Read only towards {@code tag} — no cascade; a tag outlives the orders that carry it,
     * and deleting the order removes only its rows of the join table. A list reads them for many orders at once.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "customerorder_tag",
        joinColumns = @JoinColumn(name = "customerorder_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @BatchSize(size = 100)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<Tag> tags = new HashSet<>();

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

    /**
     * How many ticket references a booking may carry (#1326); a suborder may override it. Two columns
     * rather than an embeddable: a suborder holds the same pair, nullable, and reads clearer as the same
     * two fields. Read and written as {@link #getTicketReferencePolicy()}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_reference_mode", nullable = false, columnDefinition = "varchar(16)")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private TicketReferenceMode ticketReferenceMode = TicketReferencePolicy.DEFAULT.mode();

    @Column(name = "ticket_reference_limit")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Integer ticketReferenceLimit = TicketReferencePolicy.DEFAULT.limit();

    public TicketReferencePolicy getTicketReferencePolicy() {
        return new TicketReferencePolicy(ticketReferenceMode, ticketReferenceLimit);
    }

    public void setTicketReferencePolicy(TicketReferencePolicy policy) {
        var value = policy != null ? policy : TicketReferencePolicy.DEFAULT;
        this.ticketReferenceMode = value.mode();
        this.ticketReferenceLimit = value.limit();
    }

    /**
     * Time surcharges on the overtime account (#1469), per kind; a missing rate means no surcharge of
     * that kind. Hibernate leaves the embeddable {@code null} when all five columns are, hence the
     * accessors below rather than Lombok's.
     */
    @Embedded
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private SurchargeRates surchargeRates;

    /** {@code null} where the order pays no surcharge of {@code kind}. */
    public SurchargeRate getSurchargeRate(SurchargeKind kind) {
        return surchargeRates != null ? surchargeRates.get(kind) : null;
    }

    public void setSurchargeRate(SurchargeKind kind, SurchargeRate rate) {
        if (surchargeRates == null) {
            surchargeRates = new SurchargeRates();
        }
        surchargeRates.set(kind, rate);
    }

    public String getFormattedUntilDate() {
        LocalDate untilLocalDate = getUntilDate();
        if (untilLocalDate != null) {
            return format(untilDate);
        }
        return "";
    }

    /**
     * The suborders, sorted, as a copy that cannot be changed (#1371): another module reaches the order
     * through a reference and must not change the list Hibernate manages. Sorting the copy also leaves
     * that list as it is; it used to be sorted in place on every read.
     */
    public List<Suborder> getSuborders() {
        return suborders.stream().sorted(SubOrderComparator.INSTANCE).toList();
    }

    /** Read only, as {@link #getSuborders()}; set as a whole with {@link #setResponsibleHbt}. */
    public List<Employee> getResponsibleHbt() {
        return responsibleHbt == null ? null : Collections.unmodifiableList(responsibleHbt);
    }

    /** The tags by name regardless of case, as a copy that cannot be changed; set with {@link #replaceTags}. */
    public List<Tag> getTags() {
        return tags.stream().sorted(Comparator.comparing(Tag::getNameKey)).toList();
    }

    public void replaceTags(Collection<Tag> newTags) {
        tags.clear();
        tags.addAll(newTags);
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

    /**
     * Whether one of these customers is the order's customer or its intermediary (#1536) — what a filter by customer
     * means. Reads the intermediary's id only, which does not load it.
     */
    public boolean involvesAnyOf(Collection<Long> customerIds) {
        return customerIds.contains(customer.getId())
            || (intermediary != null && customerIds.contains(intermediary.getId()));
    }

    public LocalDateRange getValidity() {
        return new LocalDateRange(getFromDate(), getUntilDate());
    }
}
