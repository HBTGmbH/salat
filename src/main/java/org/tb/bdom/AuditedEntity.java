package org.tb.bdom;

import static javax.persistence.TemporalType.TIMESTAMP;

import java.util.Date;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;

/**
 * Contains information about creation edits made to database columns common to
 * some database tables.
 *
 * @author kd
 */
@Getter
@Setter
@MappedSuperclass
abstract public class AuditedEntity implements Persistable<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Override
    public boolean isNew() {
        return id == null;
    }

}
