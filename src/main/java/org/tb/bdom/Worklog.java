package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.*;
import java.io.Serializable;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Worklog implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;
    @Column(name = "jira_Worklog_ID")
    private int jiraWorklogID;
    @Column(name = "jira_Ticket_Key")
    private String jiraTicketKey;
    /**
     * type of the worklogAction (created, updated)
     */
    private String type;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "TIMEREPORT_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Timereport timereport;
    private Integer updatecounter;

}
