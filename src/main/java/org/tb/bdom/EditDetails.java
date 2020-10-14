package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.MappedSuperclass;

/**
 * contains information about creation edits made to database columns common to some database tables
 *
 * @author kd
 */
@Getter
@Setter
@MappedSuperclass
abstract public class EditDetails {
    private java.util.Date created;
    private java.util.Date lastupdate;
    private String createdby;
    private String lastupdatedby;
    private Integer updatecounter;
}
