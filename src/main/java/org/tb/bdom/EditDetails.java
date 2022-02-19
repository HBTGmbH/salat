package org.tb.bdom;

import static javax.persistence.TemporalType.TIMESTAMP;

import java.util.Date;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * contains information about creation edits made to database columns common to some database tables
 *
 * @author kd
 */
@Getter
@Setter
@MappedSuperclass
abstract public class EditDetails {

    @CreatedDate
    @Temporal(TIMESTAMP)
    private Date created;

    @LastModifiedDate
    @Temporal(TIMESTAMP)
    private Date lastupdate;

    @CreatedBy
    private String createdby;

    @LastModifiedBy
    private String lastupdatedby;

    @Version
    private Integer updatecounter;

}
