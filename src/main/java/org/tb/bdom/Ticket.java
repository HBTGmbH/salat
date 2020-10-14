package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.*;
import java.io.Serializable;
import java.sql.Date;
import java.util.List;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUBORDER_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Suborder suborder;

    @Column(name = "jira_Ticket_Key")
    private String jiraTicketKey;

    /**
     * list of timereports, associated to this ticket
     */
    @OneToMany
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Timereport> timereports;
    private Date fromDate;
    private Date untilDate;

}
