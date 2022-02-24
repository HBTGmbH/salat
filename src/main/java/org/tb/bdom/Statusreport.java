package org.tb.bdom;

import java.io.Serializable;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Getter
@Setter
@Entity
@Table(name = "STATUSREPORT")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Statusreport extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMERORDER")
    @Cascade(CascadeType.SAVE_UPDATE)
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
    private LocalDate fromdate;
    private LocalDate untildate;
    private String allocator;
    private Byte trend;
    private Byte trendstatus;
    @Lob
    @Column(columnDefinition = "text")
    private String needforaction_text;
    private String needforaction_source;
    private Byte needforaction_status;
    @Lob
    @Column(columnDefinition = "text")
    private String aim_text;
    private String aim_source;
    @Lob
    @Column(columnDefinition = "text")
    private String aim_action;
    private Byte aim_status;
    @Lob
    @Column(columnDefinition = "text")
    private String budget_resources_date_text;
    private String budget_resources_date_source;
    @Lob
    @Column(columnDefinition = "text")
    private String budget_resources_date_action;
    private Byte budget_resources_date_status;
    @Lob
    @Column(columnDefinition = "text")
    private String riskmonitoring_text;
    private String riskmonitoring_source;
    @Lob
    @Column(columnDefinition = "text")
    private String riskmonitoring_action;
    private Byte riskmonitoring_status;
    @Lob
    @Column(columnDefinition = "text")
    private String changedirective_text;
    private String changedirective_source;
    @Lob
    @Column(columnDefinition = "text")
    private String changedirective_action;
    private Byte changedirective_status;
    @Lob
    @Column(columnDefinition = "text")
    private String communication_text;
    private String communication_source;
    @Lob
    @Column(columnDefinition = "text")
    private String communication_action;
    private Byte communication_status;
    @Lob
    @Column(columnDefinition = "text")
    private String improvement_text;
    private String improvement_source;
    @Lob
    @Column(columnDefinition = "text")
    private String improvement_action;
    private Byte improvement_status;
    @Lob
    @Column(columnDefinition = "text")
    private String customerfeedback_text;
    private String customerfeedback_source;
    private Byte customerfeedback_status;
    @Lob
    @Column(columnDefinition = "text")
    private String miscellaneous_text;
    private String miscellaneous_source;
    @Lob
    @Column(columnDefinition = "text")
    private String miscellaneous_action;
    private Byte miscellaneous_status;
    @Lob
    @Column(columnDefinition = "text")
    private String notes;
    private LocalDate released;
    private LocalDate accepted;

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
