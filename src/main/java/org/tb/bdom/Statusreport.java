package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.*;
import java.io.Serializable;

/**
 * Bean for table 'statusreport'
 *
 * @author th
 */
@Getter
@Setter
@Entity
@Table(name = "STATUSREPORT")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Statusreport implements Serializable {
    private static final long serialVersionUID = 1L; // 3L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMERORDER")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Customerorder customerorder;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SENDER")
    private Employee sender;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "RECIPIENT")
    private Employee recipient;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "RELEASEDBY")
    private Employee releasedby;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "ACCEPTEDBY")
    private Employee acceptedby;

    private Byte sort;
    private Byte phase;
    private java.sql.Date fromdate;
    private java.sql.Date untildate;
    private String allocator;
    private Byte trend;
    private Byte trendstatus;
    private String needforaction_text;
    private String needforaction_source;
    private Byte needforaction_status;
    private String aim_text;
    private String aim_source;
    private String aim_action;
    private Byte aim_status;
    private String budget_resources_date_text;
    private String budget_resources_date_source;
    private String budget_resources_date_action;
    private Byte budget_resources_date_status;
    private String riskmonitoring_text;
    private String riskmonitoring_source;
    private String riskmonitoring_action;
    private Byte riskmonitoring_status;
    private String changedirective_text;
    private String changedirective_source;
    private String changedirective_action;
    private Byte changedirective_status;
    private String communication_text;
    private String communication_source;
    private String communication_action;
    private Byte communication_status;
    private String improvement_text;
    private String improvement_source;
    private String improvement_action;
    private Byte improvement_status;
    private String customerfeedback_text;
    private String customerfeedback_source;
    private Byte customerfeedback_status;
    private String miscellaneous_text;
    private String miscellaneous_source;
    private String miscellaneous_action;
    private Byte miscellaneous_status;
    private String notes;
    private java.util.Date released;
    private java.util.Date accepted;
    private java.util.Date created;
    private String createdby;
    private java.util.Date lastupdate;
    private String lastupdatedby;
    private Integer updatecounter;

    public Byte getOverallStatus() {
        Byte status = 0;
        if (getTrendstatus() > status) {
            status = getTrendstatus();
        }
        if (getNeedforaction_status() > status) {
            status = getNeedforaction_status();
        }
        if (getAim_status() > status) {
            status = getAim_status();
        }
        if (getBudget_resources_date_status() > status) {
            status = getBudget_resources_date_status();
        }
        if (getRiskmonitoring_status() > status) {
            status = getRiskmonitoring_status();
        }
        if (getChangedirective_status() > status) {
            status = getChangedirective_status();
        }
        if (getCommunication_status() > status) {
            status = getCommunication_status();
        }
        if (getImprovement_status() > status) {
            status = getImprovement_status();
        }
        if (getMiscellaneous_status() > status) {
            status = getMiscellaneous_status();
        }
        return status;
    }

}
