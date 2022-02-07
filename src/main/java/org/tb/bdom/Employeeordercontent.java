package org.tb.bdom;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Data
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "EMPLOYEEORDERCONTENT")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employeeordercontent extends EditDetails implements Serializable {
    private static final long serialVersionUID = 1L; // 2L;

    @Id
    @GeneratedValue
    private long id;

    /**
     * Responsible Contract HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CONTACT_CONTRACT_HBT")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employee contactContractHbt;

    /**
     * Responsible Technical HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CONTACT_TECH_HBT")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employee contactTechHbt;

    /**
     * Responsible Technical HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "COMMITTEDBY_MGMT")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employee committedby_mgmt;

    /**
     * Responsible Technical HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "COMMITTEDBY_EMP")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employee committedby_emp;

    private String description;
    private String task;
    private String boundary;
    private String procedure;
    private Integer qm_process_id;
    private String contact_contract_customer;
    private String contact_tech_customer;
    private String additional_risks;
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
