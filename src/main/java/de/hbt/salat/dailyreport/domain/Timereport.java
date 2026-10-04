package de.hbt.salat.dailyreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;

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

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    private Employeecontract employeecontract;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUBORDER_ID")
    private Suborder suborder;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEEORDER_ID")
    private Employeeorder employeeorder;

    private Integer durationhours;
    private Integer durationminutes;
    @Lob
    @Column(columnDefinition = "text")
    private String taskdescription;
    private String status;
    /**
     * Optional free text reference to an external ticket (#982), in practice the JIRA issue key.
     *
     * <p>A deliberate exception to the rule that a record refers to another by id (AGENTS.md, #1205):
     * the key is matched against {@code JiraTicket.key} as text, ignoring case, and there is no
     * foreign key. An id would not do —
     * <ul>
     *   <li>the reference is typed and may name a ticket that was never replicated, or not yet;</li>
     *   <li>the same key may be replicated under several scopes, so there is no single row to point
     *       at;</li>
     *   <li>a replicated ticket can disappear and come back (#1167) under a new row, and the booking
     *       must still name it.</li>
     * </ul>
     * The worklog sync ({@code JiraWorklogSync.issueKey}) and the parent chains
     * ({@code JiraTicket.parentKey}) rely on the same key, and a favourite carries it the same way.
     * The key belongs to JIRA, not to SALAT: it changes only when a ticket is moved to another
     * project there, and a booking then keeps the key it was booked on.
     */
    @Column(name = "ticket_reference")
    private String ticketReference;
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
        timereport.setEmployeecontract(employeecontract);
        timereport.setStatus(status);
        timereport.setSuborder(suborder);
        timereport.setTaskdescription(taskdescription);
        timereport.setTicketReference(ticketReference);
        timereport.setTraining(training);
        timereport.setSequencenumber(0);
        timereport.setEmployeeorder(employeeorder);
        timereport.setReferenceday(referenceday);
        return timereport;
    }

    public boolean getFitsToContract() {
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

    public Duration getDuration() {
        return Duration.ofHours(durationhours).plusMinutes(durationminutes);
    }

}
