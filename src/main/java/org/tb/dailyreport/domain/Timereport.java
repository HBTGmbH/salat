package org.tb.dailyreport.domain;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.AuditedEntity;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Timereport extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "REFERENCEDAY_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Referenceday referenceday;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employeecontract employeecontract;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUBORDER_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Suborder suborder;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEEORDER_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employeeorder employeeorder;

    private Integer durationhours;
    private Integer durationminutes;
    @Lob
    @Column(columnDefinition = "text")
    private String taskdescription;
    private String status;
    private Boolean training; // TODO switch to boolean
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

    public Timereport getTwin() {
        Timereport timereport = new Timereport();
        timereport.setDurationhours(durationhours);
        timereport.setDurationminutes(durationminutes);
        timereport.setEmployeecontract(employeecontract);
        timereport.setStatus(status);
        timereport.setSuborder(suborder);
        timereport.setTaskdescription(taskdescription);
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
                + getSuborder().getSign() + " | " + getDurationhours() + ":"
                + getDurationminutes() + " | " + getTaskdescription() + " | "
                + getStatus() + "]";
    }

    public Duration getDuration() {
        return Duration.ofHours(durationhours).plusMinutes(durationminutes);
    }

}
