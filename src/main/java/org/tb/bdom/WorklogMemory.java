package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.*;
import java.io.Serializable;

/**
 * Bean for table 'worklogMemory'
 *
 * @author jh
 */
@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class WorklogMemory implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @Column(name = "operation")
    private int operation;

    @Column(name = "issue_ID")
    private String issueID;

    @Column(name = "worklog_ID")
    private int worklogID;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "TIMEREPORT_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Timereport timereport;

}
