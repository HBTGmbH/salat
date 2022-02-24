package org.tb.order;

import java.io.Serializable;
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
import org.tb.common.AuditedEntity;
import org.tb.employee.Employee;

@Getter
@Setter
@Entity
@Table(name = "EMPLOYEEORDERCONTENT")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employeeordercontent extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L; // 2L;

    /**
     * Responsible Contract HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CONTACT_CONTRACT_HBT")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employee contactContractHbt;

    /**
     * Responsible Technical HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CONTACT_TECH_HBT")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employee contactTechHbt;

    /**
     * Responsible Technical HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "COMMITTEDBY_MGMT")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employee committedby_mgmt;

    /**
     * Responsible Technical HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "COMMITTEDBY_EMP")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employee committedby_emp;

    @Lob
    @Column(columnDefinition = "text")
    private String description;
    @Lob
    @Column(columnDefinition = "text")
    private String task;
    @Lob
    @Column(columnDefinition = "text")
    private String boundary;
    @Lob
    @Column(columnDefinition = "text")
    private String procedure;
    private Integer qm_process_id;
    private String contact_contract_customer;
    private String contact_tech_customer;
    @Lob
    @Column(columnDefinition = "text")
    private String additional_risks;
    @Lob
    @Column(columnDefinition = "text")
    private String arrangement;
    private Boolean committed_mgmt;
    private Boolean committed_emp;

    public Boolean getCommitted_emp() {
        return committed_emp != null && committed_emp;
    }

    public Boolean getCommitted_mgmt() {
        return committed_mgmt != null && committed_mgmt;
    }

}
