package org.tb.bdom;

import static javax.persistence.TemporalType.TIMESTAMP;

import java.util.Date;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import lombok.Getter;
import lombok.Setter;

/**
 * contains information about creation edits made to database columns common to some database tables
 *
 * @author kd
 */
@Getter
@Setter
@MappedSuperclass
abstract public class EditDetails {
    @Temporal(TIMESTAMP)
    private Date created;
    @Temporal(TIMESTAMP)
    private Date lastupdate;
    private String createdby;
    private String lastupdatedby;
    private Integer updatecounter;
}
