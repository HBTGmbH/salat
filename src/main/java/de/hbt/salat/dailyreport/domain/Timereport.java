package de.hbt.salat.dailyreport.domain;

import static de.hbt.salat.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * A booking. Its suborder and its contract are those of its employee order (#1210): the booking
 * carries no copy of either, and {@link #getSuborder()} and {@link #getEmployeecontract()} answer
 * from {@link #employeeorder}.
 */
@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SQLDelete(sql = "UPDATE timereport SET deleted = true WHERE id=? and updatecounter=?")
@SQLRestriction("deleted = false")
public class Timereport extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "REFERENCEDAY_ID")
    private Referenceday referenceday;

    /**
     * The employee order booked on. It decides the suborder and the contract of the booking (#1210);
     * a booking without one does not exist.
     */
    @ManyToOne(optional = false)
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEEORDER_ID", nullable = false)
    private Employeeorder employeeorder;

    private Integer durationhours;
    private Integer durationminutes;
    @Lob
    @Column(columnDefinition = "text")
    private String taskdescription;
    private String status;
    /**
     * The ticket references of the booking (#982, #1326), in practice JIRA issue keys, in the order
     * they were given. How many it may carry is set on its order or suborder
     * ({@code Suborder#getEffectiveTicketReferencePolicy}); {@code TimereportService} checks that and
     * stores them under the rule of {@code TicketReferences}.
     *
     * <p>A deliberate exception to the rule that a record refers to another by id (AGENTS.md, #1205):
     * a key is matched against {@code JiraTicket.key} as text, ignoring case, and there is no
     * foreign key. An id would not do —
     * <ul>
     *   <li>a reference is typed and may name a ticket that was never replicated, or not yet;</li>
     *   <li>the same key may be replicated under several scopes, so there is no single row to point
     *       at;</li>
     *   <li>a replicated ticket can disappear and come back (#1167) under a new row, and the booking
     *       must still name it.</li>
     * </ul>
     * The worklog sync ({@code JiraWorklogSync.issueKey}) and the parent chains
     * ({@code JiraTicket.parentKey}) rely on the same key, and a favourite carries it the same way.
     * The key belongs to JIRA, not to SALAT: it changes only when a ticket is moved to another
     * project there, and a booking then keeps the key it was booked on.
     *
     * <p>Positions count from 1. Lists are loaded in batches, so a list of bookings costs one query
     * for all their references rather than one per booking. Deleting a booking removes its references
     * along with it, even though the booking itself is only marked deleted ({@code @SQLDelete}):
     * Hibernate clears a collection before it deletes its owner, and nothing reads a deleted booking.
     * The hard delete of soft-deleted bookings is a native statement, which the foreign key's
     * {@code ON DELETE CASCADE} covers.
     */
    @ElementCollection
    @CollectionTable(name = "timereport_ticket_reference",
        joinColumns = @JoinColumn(name = "timereport_id",
            foreignKey = @ForeignKey(name = "fk_timereport_ticket_reference_timereport")),
        uniqueConstraints = @UniqueConstraint(name = "uk_timereport_ticket_reference_timereport_id_reference",
            columnNames = {"timereport_id", "reference"}),
        indexes = @Index(name = "idx_timereport_ticket_reference_reference", columnList = "reference"))
    @OrderColumn(name = "position", nullable = false)
    @ListIndexBase(1)
    @Column(name = "reference", nullable = false, length = TICKET_REFERENCE_MAX_LENGTH)
    @BatchSize(size = 100)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<String> ticketReferences = new ArrayList<>();

    /** Training on the job (#836); a column with {@code NOT NULL DEFAULT false} since #1246. */
    @Column(nullable = false)
    private boolean training;
    private int sequencenumber;
    /**
     * Sign of the releasing person
     */
    private String releasedby;
    private LocalDateTime released;
    /**
     * Sign of the accepting person
     */
    private String acceptedby;
    private LocalDateTime accepted;

    private boolean deleted;

    public Timereport getTwin() {
        Timereport timereport = new Timereport();
        timereport.setDurationhours(durationhours);
        timereport.setDurationminutes(durationminutes);
        timereport.setStatus(status);
        timereport.setTaskdescription(taskdescription);
        timereport.setTicketReferences(ticketReferences);
        timereport.setTraining(training);
        timereport.setSequencenumber(0);
        timereport.setEmployeeorder(employeeorder);
        timereport.setReferenceday(referenceday);
        return timereport;
    }

    /** The contract of the booking: that of its employee order since #1210. */
    public Employeecontract getEmployeecontract() {
        return employeeorder.getEmployeecontract();
    }

    /** The suborder of the booking: that of its employee order since #1210. */
    public Suborder getSuborder() {
        return employeeorder.getSuborder();
    }

    public boolean getFitsToContract() {
        var employeecontract = getEmployeecontract();
        return !referenceday.getRefdate().isBefore(employeecontract.getValidFrom())
               && (employeecontract.getValidUntil() == null || !referenceday.getRefdate().isAfter(employeecontract.getValidUntil()));
    }

    public String getTimeReportAsString() {
        return "TR[" + getEmployeecontract().getEmployee().getSign() + " | "
                + DateUtils.format(getReferenceday().getRefdate()) + " | "
                + getSuborder().getCustomerorder().getSign() + " / "
                + getSuborder().getCompleteOrderSign() + " | " + getDurationhours() + ":"
                + getDurationminutes() + " | " + getTaskdescription() + " | "
                + getStatus() + "]";
    }

    public List<String> getTicketReferences() {
        return Collections.unmodifiableList(ticketReferences);
    }

    /**
     * Replaces the references; the caller has normalized them. An unchanged list leaves the
     * collection alone. A changed one is swapped for a new instance rather than edited in place:
     * Hibernate then deletes the old rows before it inserts the new ones, where updating rows by
     * position would trip over the unique key when two references only trade places.
     */
    public void setTicketReferences(List<String> references) {
        var values = references == null ? List.<String>of() : List.copyOf(references);
        if (values.equals(ticketReferences)) {
            return;
        }
        this.ticketReferences = new ArrayList<>(values);
    }

    public Duration getDuration() {
        return Duration.ofHours(durationhours).plusMinutes(durationminutes);
    }

}
