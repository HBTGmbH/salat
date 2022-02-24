package org.tb.bdom;

import static javax.persistence.TemporalType.TIMESTAMP;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
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
    private LocalDateTime created;

    @LastModifiedDate
    @Temporal(TIMESTAMP)
    private LocalDateTime lastupdate;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if(id == null) return false;
        AuditedEntity that = (AuditedEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        if(id == null) return 0;
        return Objects.hash(id);
    }

}
