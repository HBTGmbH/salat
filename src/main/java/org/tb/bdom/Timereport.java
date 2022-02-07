package org.tb.bdom;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.util.DateUtils;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Timereport extends EditDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "REFERENCEDAY_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Referenceday referenceday;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employeecontract employeecontract;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUBORDER_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Suborder suborder;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEEORDER_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employeeorder employeeorder;

    private Integer durationhours;
    private Integer durationminutes;
    private String sortofreport; // TODO was steckt hinter diesem Konzept? klären!
    private String taskdescription;
    private String status;
    private Double costs;
    private Boolean training; // TODO switch to boolean
    private int sequencenumber;
    /**
     * Sign of the releasing person
     */
    private String releasedby;
    private java.util.Date released;
    /**
     * Sign of the accepting person
     */
    private String acceptedby;
    private java.util.Date accepted;

    public Timereport getTwin() {
        Timereport timereport = new Timereport();
        timereport.setCosts(costs);
        timereport.setDurationhours(durationhours);
        timereport.setDurationminutes(durationminutes);
        timereport.setEmployeecontract(employeecontract);
        timereport.setSortofreport(sortofreport);
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
        return !referenceday.getRefdate().before(employeecontract.getValidFrom()) && (employeecontract.getValidUntil() == null || !referenceday.getRefdate().after(employeecontract.getValidUntil()));
    }

    public String getTimeReportAsString() {
        return "TR[" + getEmployeecontract().getEmployee().getSign() + " | "
                + DateUtils.format(getReferenceday().getRefdate()) + " | "
                + getSuborder().getCustomerorder().getSign() + " / "
                + getSuborder().getSign() + " | " + getDurationhours() + ":"
                + getDurationminutes() + " | " + getTaskdescription() + " | "
                + getStatus() + "]";
    }

}
