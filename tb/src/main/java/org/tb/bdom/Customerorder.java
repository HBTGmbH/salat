package org.tb.bdom;

import java.io.Serializable;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.GlobalConstants;
import org.tb.bdom.comparators.SubOrderComparator;

/**
 * Bean for table 'customerorder'.
 * 
 * @author oda
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Customerorder implements Serializable {
    
    private static final long serialVersionUID = 1L; // 1L;
    
    /**
     * Autogenerated technical object id.
     */
    @Id
    @GeneratedValue
    private long id;
    
    /** Customer */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMER_ID")
    @Cascade(value = { CascadeType.SAVE_UPDATE })
    private Customer customer;
    
    /** list of suborders, associated to this customerorder */
    @OneToMany(mappedBy = "customerorder")
    @Cascade(value = { CascadeType.SAVE_UPDATE })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Suborder> suborders;
    
    /**
     * list of ProjectIDs, associated to this customerorder */
    @OneToMany(mappedBy = "customerorder")
    @Cascade(value = { CascadeType.SAVE_UPDATE })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<ProjectID> projectIDs;
    
    /** Responsible of Customer */
    private String responsible_customer_technical;
    private String responsible_customer_contractually;
    
    /** Responsible employee of HBT */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "RESPONSIBLE_HBT_ID")
    @Cascade(value = { CascadeType.SAVE_UPDATE })
    private Employee responsible_hbt;
    
    /** contractually responsible employee of HBT */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "RESPONSIBLE_HBT_CONTRACTUALLY_ID")
    @Cascade(value = { CascadeType.SAVE_UPDATE })
    private Employee respEmpHbtContract;
    
    /** Orderer of Customer */
    private String order_customer;
    
    /** from date */
    private Date fromDate;
    
    /** until date */
    private Date untilDate;
    
    /** Sign */
    private String sign;
    
    /** Description */
    private String description;
    
    /**  Short Description */
    private String shortdescription;
    
    /** Currency */
    private String currency;
    
    /** Hourly Rate */
    private Double hourly_rate;
    
    /** Creation Date */
    private java.util.Date created;
    
    /** Last Update */
    private java.util.Date lastupdate;
    
    /** Created By */
    private String createdby;
    
    /** Updated By */
    private String lastupdatedby;
    
    /** Update Counter */
    private Integer updatecounter;
    
    /** Debit hours */
    private Double debithours;
    
    /** Unit of the debit hours */
    private Byte debithoursunit;
    
    /** Statusreport */
    private Integer statusreport;
    
    /** Hide in select boxes */
    private Boolean hide;
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public Customer getCustomer() {
        return customer;
    }
    
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
    
    public String getOrder_customer() {
        return order_customer;
    }
    
    public void setOrder_customer(String orderCustomer) {
        this.order_customer = orderCustomer;
    }
    
    /**
     * @return the responsible_customer_contractually
     */
    public String getResponsible_customer_contractually() {
        return responsible_customer_contractually;
    }
    
    /**
     * @param responsible_customer_contractually the responsible_customer_contractually to set
     */
    public void setResponsible_customer_contractually(
            String responsible_customer_contractually) {
        this.responsible_customer_contractually = responsible_customer_contractually;
    }
    
    /**
     * @return the responsible_customer_technical
     */
    public String getResponsible_customer_technical() {
        return responsible_customer_technical;
    }
    
    /**
     * @param responsible_customer_technical the responsible_customer_technical to set
     */
    public void setResponsible_customer_technical(
            String responsible_customer_technical) {
        this.responsible_customer_technical = responsible_customer_technical;
    }
    
    /**
     * @return the responsible_hbt
     */
    public Employee getResponsible_hbt() {
        return responsible_hbt;
    }
    
    /**
     * @param responsible_hbt the responsible_hbt to set
     */
    public void setResponsible_hbt(Employee responsible_hbt) {
        this.responsible_hbt = responsible_hbt;
    }
    
    /**
     * @return the respEmpHbtContract
     */
    public Employee getRespEmpHbtContract() {
        return respEmpHbtContract;
    }
    
    /**
     * @param respEmpHbtContract the respEmpHbtContract to set
     */
    public void setRespEmpHbtContract(Employee respEmpHbtContract) {
        this.respEmpHbtContract = respEmpHbtContract;
    }
    
    /**
     * @return the statusreport
     */
    public Integer getStatusreport() {
        if (statusreport == null) {
            return 0;
        }
        return statusreport;
    }
    
    /**
     * @param statusreport the statusreport to set
     */
    public void setStatusreport(Integer statusreport) {
        this.statusreport = statusreport;
    }
    
    public Date getFromDate() {
        return fromDate;
    }
    
    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }
    
    public Date getUntilDate() {
        return untilDate;
    }
    
    public void setUntilDate(Date untilDate) {
        this.untilDate = untilDate;
    }
    
    public String getFormattedUntilDate() {
        Date untilDate = getUntilDate();
        if (untilDate != null) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
            return simpleDateFormat.format(untilDate);
        }
        return "";
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getSign() {
        return sign;
    }
    
    public void setSign(String sign) {
        this.sign = sign;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public Double getHourly_rate() {
        return hourly_rate;
    }
    
    public void setHourly_rate(Double hourly_rate) {
        this.hourly_rate = hourly_rate;
    }
    
    public List<Suborder> getSuborders() {
        Collections.sort(suborders, new SubOrderComparator());
        return suborders;
    }
    
    public void setSuborders(List<Suborder> suborders) {
        this.suborders = suborders;
    }
    
    public List<ProjectID> getProjectIDs() {
        return projectIDs;
    }
    
    public void setProjectIDs(List<ProjectID> projectIDs) {
        this.projectIDs = projectIDs;
    }
    
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
    
    public String getShortdescription() {
        if (shortdescription == null || shortdescription.equals("")) {
            if (description == null) {
                description = "";
            }
            if (description.length() > 20) {
                return description.substring(0, 17) + "...";
            } else {
                return description;
            }
        }
        return shortdescription;
    }
    
    public void setShortdescription(String shortdescription) {
        this.shortdescription = shortdescription;
    }
    
    public String getSignAndDescription() {
        return sign + " - " + getShortdescription();
    }
    
    /**
     * @return the debithours
     */
    public Double getDebithours() {
        return debithours;
    }
    
    /**
     * @param debithours the debithours to set
     */
    public void setDebithours(Double debithours) {
        this.debithours = debithours;
    }
    
    /**
     * @return the debithoursunit
     */
    public Byte getDebithoursunit() {
        return debithoursunit;
    }
    
    /**
     * @param debithoursunit the debithoursunit to set
     */
    public void setDebithoursunit(Byte debithoursunit) {
        this.debithoursunit = debithoursunit;
    }
    
    /**
     * @return the hide
     */
    public Boolean getHide() {
        if (hide == null) {
            return false;
        }
        return hide;
    }
    
    /**
     * @param hide the hide to set
     */
    public void setHide(Boolean hide) {
        this.hide = hide;
    }
    
    /**
     * 
     * @return Returns true, if the {@link Customerorder} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        java.util.Date now = new java.util.Date();
        if (!now.before(getFromDate()) && (getUntilDate() == null || !now.after(getUntilDate()))) {
            return true;
        }
        return false;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Customerorder) {
            Customerorder other = (Customerorder)obj;
            return other.getSign().equals(getSign());
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return getSign().hashCode();
    }
    
}
