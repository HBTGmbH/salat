package org.tb.bdom;

import javax.persistence.MappedSuperclass;

/**
 * contains information about creation edits made to database columns common to some database tables
 *
 * @author kd
 */
@MappedSuperclass
abstract public class EditDetails {
    /**
     * Creation Date
     */
    private java.util.Date created;

    /**
     * Last Update
     */
    private java.util.Date lastupdate;

    /**
     * Created By
     */
    private String createdby;

    /**
     * Updated By
     */
    private String lastupdatedby;

    /**
     * Update Counter
     */
    private Integer updatecounter;


    /**
     * @return the created
     */
    public java.util.Date getCreated() {
        return created;
    }

    /**
     * @param created the created to set
     */
    public void setCreated(java.util.Date created) {
        this.created = created;
    }

    /**
     * @return the createdby
     */
    public String getCreatedby() {
        return createdby;
    }

    /**
     * @param createdby the createdby to set
     */
    public void setCreatedby(String createdby) {
        this.createdby = createdby;
    }

    /**
     * @return the lastupdate
     */
    public java.util.Date getLastupdate() {
        return lastupdate;
    }

    /**
     * @param lastupdate the lastupdate to set
     */
    public void setLastupdate(java.util.Date lastupdate) {
        this.lastupdate = lastupdate;
    }

    /**
     * @return the lastupdatedby
     */
    public String getLastupdatedby() {
        return lastupdatedby;
    }

    /**
     * @param lastupdatedby the lastupdatedby to set
     */
    public void setLastupdatedby(String lastupdatedby) {
        this.lastupdatedby = lastupdatedby;
    }

    /**
     * @return the updatecounter
     */
    public Integer getUpdatecounter() {
        return updatecounter;
    }

    /**
     * @param updatecounter the updatecounter to set
     */
    public void setUpdatecounter(Integer updatecounter) {
        this.updatecounter = updatecounter;
    }

}
