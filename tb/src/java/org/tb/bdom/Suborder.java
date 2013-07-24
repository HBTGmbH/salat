package org.tb.bdom;

import java.io.Serializable;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
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
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;

/**
 * Bean for table 'suborder'.
 * 
 * @author oda
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Suborder implements Serializable {
    
    private static final long serialVersionUID = 1L; // 1L;
    
    /**
     * Autogenerated technical object id.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id = -1;
    
    /** Customerorder */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMERORDER_ID")
    private Customerorder customerorder;
    
    /** list of timereports, associated to this suborder */
    @OneToMany(mappedBy = "suborder")
    @Cascade(value = { CascadeType.SAVE_UPDATE })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Timereport> timereports;
    
    /** list of employeeorders, associated to this suborder */
    @OneToMany(mappedBy = "suborder")
    @Cascade(value = { CascadeType.SAVE_UPDATE })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Employeeorder> employeeorders;
    
    /** list of children */
    @OneToMany(mappedBy = "suborder")
    @Cascade(value = { CascadeType.SAVE_UPDATE })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Suborder> children;
    
    /** parentorder */
    @ManyToOne(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "PARENTORDER_ID")
    private Suborder suborder;
    
    //	@PostLoad
    //	public void initChildren() {
    //		System.err.println("init suborder " + id);
    //		Hibernate.initialize(suborder);
    //		Hibernate.initialize(children);
    //		for (Suborder child : children) {
    //			Hibernate.initialize(child);
    //		}
    //	}
    
    /** Customer subordersign */
    private String suborder_customer;
    
    /** Invoice */
    private char invoice;
    
    /** Sign */
    private String sign;
    
    /** Description */
    private String description;
    
    /** Short Description */
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
    
    /** STANDARD */
    private Boolean standard;
    
    /** Comment necessary */
    private Boolean commentnecessary;
    
    /** from date */
    private Date fromDate;
    
    /** until date */
    private Date untilDate;
    
    /** Debit hours */
    private Double debithours;
    
    /** Unit of the debit hours */
    private Byte debithoursunit;
    
    /** Hide in select boxes */
    private Boolean hide;
    
    /** No employee order content for employee orders */
    @Column(name = "NOEMPLOYEEORDERCONTENT")
    private Boolean noEmployeeOrderContent;
    
    /** Default-Flag for projectbased Training */
    private Boolean trainingFlag;
    
    /** Flag for fixed price proposal */
    private Boolean fixedPrice;
    
    public void addSuborder(Suborder child) {
        if (children == null) {
            children = new LinkedList<Suborder>();
        }
        children.add(child);
    }
    
    public Suborder getParentorder() {
        return suborder;
    }
    
    public void setParentorder(Suborder parentorder) {
        this.suborder = parentorder;
    }
    
    public List<Suborder> getSuborders() {
        return children;
    }
    
    public void setSuborders(List<Suborder> suborders) {
        this.children = suborders;
    }
    
    public String getSuborder_customer() {
        return suborder_customer;
    }
    
    public void setSuborder_customer(String suborder_customer) {
        this.suborder_customer = suborder_customer;
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public char getInvoice() {
        return invoice;
    }
    
    public void setInvoice(char invoice) {
        this.invoice = invoice;
    }
    
    public Customerorder getCustomerorder() {
        return customerorder;
    }
    
    public void setCustomerorder(Customerorder order) {
        this.customerorder = order;
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
    
    public List<Timereport> getTimereports() {
        return timereports;
    }
    
    public void setTimereports(List<Timereport> timereports) {
        this.timereports = timereports;
    }
    
    public List<Employeeorder> getEmployeeorders() {
        return employeeorders;
    }
    
    public void setEmployeeorders(List<Employeeorder> employeeorders) {
        this.employeeorders = employeeorders;
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
    
    /**
     * @return the noEmployeeOrderContent
     */
    public Boolean getNoEmployeeOrderContent() {
        return noEmployeeOrderContent == null ? false : noEmployeeOrderContent;
    }
    
    /**
     * @param noEmployeeOrderContent the noEmployeeOrderContent to set
     */
    public void setNoEmployeeOrderContent(Boolean noEmployeeOrderContent) {
        this.noEmployeeOrderContent = noEmployeeOrderContent;
    }
    
    public Boolean getTrainingFlag() {
        return trainingFlag;
    }
    
    public void setTrainingFlag(Boolean trainingFlag) {
        this.trainingFlag = trainingFlag;
    }
    
    public Boolean getFixedPrice() {
        return fixedPrice;
    }
    
    public void setFixedPrice(Boolean fixedPrice) {
        this.fixedPrice = fixedPrice;
    }
    
    /**
     * @return the standard
     */
    public Boolean getStandard() {
        return standard;
    }
    
    /**
     * @param standard the standard to set
     */
    public void setStandard(Boolean standard) {
        this.standard = standard;
    }
    
    public Boolean getCommentnecessary() {
        if (commentnecessary == null) {
            return false;
        }
        return commentnecessary;
    }
    
    public void setCommentnecessary(Boolean commentnecessary) {
        this.commentnecessary = commentnecessary;
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
     * @return the fromDate
     */
    public Date getFromDate() {
        if (fromDate == null) {
            if (suborder != null) {
                return suborder.getFromDate();
            }
            return getCustomerorder().getFromDate();
        }
        return fromDate;
    }
    
    /**
     * @param fromDate the fromDate to set
     */
    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }
    
    /**
     * @return the hide
     */
    public Boolean getHide() {
        return hide == null ? false : hide;
    }
    
    /**
     * @param hide the hide to set
     */
    public void setHide(Boolean hide) {
        this.hide = hide;
    }
    
    public String getTimeString() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        String result = simpleDateFormat.format(getFromDate()) + " - ";
        if (getUntilDate() != null) {
            result += simpleDateFormat.format(getUntilDate());
        }
        return result;
    }
    
    public Boolean getOpenEnd() {
        if (getUntilDate() == null) {
            return true;
        }
        return false;
    }
    
    /**
     * @return the untilDate
     */
    public Date getUntilDate() {
        if (untilDate == null) {
            if (suborder != null) {
                return suborder.getUntilDate();
            }
            return getCustomerorder().getUntilDate();
        }
        return untilDate;
    }
    
    /**
     * @param untilDate the untilDate to set
     */
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
    
    public String getSignAndDescription() {
        return getSign() + " - " + getShortdescription();
    }
    
    public String getSignAndDescriptionWithExpirationDate() {
        String result = getSign() + " - " + getShortdescription();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        Date from = getFromDate();
        Date until = getUntilDate();
        if (from != null && until != null) {
            result += " (" + simpleDateFormat.format(from) + " - " + simpleDateFormat.format(until) + ")";
        }
        return result;
    }
    
    /**
     * 
     * @return Returns true, if the {@link Suborder} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        java.util.Date now = new java.util.Date();
        if (!now.before(getFromDate()) && (getUntilDate() == null || !now.after(getUntilDate()))) {
            return true;
        }
        return false;
    }
    
    /**
     * Checks, if time period fits to the next higher hierachical element.
     * 
     * @return Returns true, if 
     * - time period fits to hierachical higher {@link Suborder} if existing 
     * or
     * - time period fits to associated {@link Customerorder}
     */
    public boolean getTimePeriodFitsToUpperElement() {
        if (suborder != null) {
            return !this.getFromDate().before(suborder.getFromDate())
                    && (suborder.getUntilDate() == null
                    || this.getUntilDate() != null
                            && suborder.getUntilDate() != null
                            && !this.getUntilDate().after(suborder.getUntilDate()));
        } else {
            return !this.getFromDate().before(customerorder.getFromDate())
                    && (customerorder.getUntilDate() == null
                    || this.getUntilDate() != null
                            && customerorder.getUntilDate() != null
                            && !this.getUntilDate().after(customerorder.getUntilDate()));
        }
    }
    
    /**
     * 
     * @return Returns true, if the valitidy period fits to the validity period of the customer order
     */
    public boolean validityPeriodFitsToCustomerOrder() {
        if (!getFromDate().before(getCustomerorder().getFromDate()) &&
                (getCustomerorder().getUntilDate() == null ||
                getUntilDate() != null && !getUntilDate().after(getCustomerorder().getUntilDate()))) {
            return true;
        }
        return false;
    }
    
    public String getInvoiceString() {
        return (Character)invoice == null ? GlobalConstants.INVOICE_UNDEFINED.toString() : ((Character)invoice).toString();
    }
    
    public Character getInvoiceChar() {
        return (Character)invoice == null ? GlobalConstants.INVOICE_UNDEFINED : (Character)invoice;
    }
    
    /**
     * Gets all children including their children and so on.
     * 
     * @return a list of {@link Suborder}s
     */
    public List<Suborder> getAllChildren() {
        
        /* build up result list */
        final List<Suborder> allChildren = new LinkedList<Suborder>();
        
        /* create visitor to collect suborders */
        SuborderVisitor allChildrenCollector = new SuborderVisitor() {
            
            public void visitSuborder(Suborder suborder) {
                allChildren.add(suborder);
            }
        };
        
        /* start visiting */
        acceptVisitor(allChildrenCollector);
        
        /* return result */
        return allChildren;
    }
    
    /**
     * Gets all {@link Timereport}s associated to the {@link Suborder} or his children, that are invalid for the given dates.
     * 
     * @param begin
     * @param end
     * @param timereportDAO
     * 
     * @return a list of {@link Timereport}s
     */
    public List<Timereport> getAllTimeReportsInvalidForDates(Date begin, Date end, TimereportDAO timereportDAO) {
        
        /* build up result list */
        final List<Timereport> allInvalidTimeReports = new LinkedList<Timereport>();
        final Date visitorBeginDate = begin;
        final Date visitorEndDate = end;
        final TimereportDAO visitorTimereportDAO = timereportDAO;
        
        /* create visitor to collect suborders */
        SuborderVisitor allInvalidTimeReportsCollector = new SuborderVisitor() {
            
            public void visitSuborder(Suborder suborder) {
                allInvalidTimeReports.addAll(visitorTimereportDAO.getTimereportsBySuborderIdInvalidForDates(visitorBeginDate, visitorEndDate, suborder.getId()));
            }
        };
        
        /* start visiting */
        acceptVisitor(allInvalidTimeReportsCollector);
        
        /* return result */
        return allInvalidTimeReports;
    }
    
    /**
     * Set the {@link Customerorder} for all descendants
     * 
     */
    public void setCustomerOrderForAllDescendants(Customerorder customerOrder, SuborderDAO suborderDAO, Employee loginEmployee, Suborder rootSuborder) {
        
        final Customerorder customerOrderToSet = customerOrder;
        final SuborderDAO visitorSuborderDAO = suborderDAO;
        final Employee visitorLoginEmployee = loginEmployee;
        final Suborder visitorRootSuborder = rootSuborder;
        
        /* create visitor to collect suborders */
        SuborderVisitor customerOrderSetter = new SuborderVisitor() {
            
            public void visitSuborder(Suborder suborder) {
                // do not modify root suborder
                if (visitorRootSuborder.getId() != suborder.getId()) {
                    Suborder suborderToModify = visitorSuborderDAO
                            .getSuborderById(suborder.getId());
                    if (suborderToModify != null) {
                        suborderToModify.setCustomerorder(customerOrderToSet);
                        // save suborder
                        visitorSuborderDAO.save(suborderToModify,
                                visitorLoginEmployee);
                    }
                }
            }
        };
        
        /* start visiting */
        acceptVisitor(customerOrderSetter);
        
    }
    
    /**
     * TODO comment
     * 
     * @param visitor
     */
    public void acceptVisitor(SuborderVisitor visitor) {
        visitor.visitSuborder(this);
        for (Suborder suborder : children) {
            suborder.acceptVisitor(visitor);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Suborder) {
            Suborder other = (Suborder)obj;
            return other.getSign().equals(getSign());
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return getSign().hashCode();
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Suborder_" + id + ": (" + sign + " " + description + ")";
    }
    
    public Suborder copy(boolean copyroot, String creator) {
        Suborder copy = new Suborder();
        
        // set attrib values in copy
        copy.setCommentnecessary(commentnecessary);
        copy.setCreated(new java.util.Date());
        copy.setCreatedby(creator + "_treecopy");
        copy.setCurrency(currency);
        copy.setCustomerorder(customerorder);
        copy.setDebithours(debithours);
        copy.setDebithoursunit(debithoursunit);
        copy.setDescription(description);
        copy.setFromDate(fromDate);
        copy.setHide(hide);
        copy.setHourly_rate(hourly_rate);
        copy.setInvoice(invoice);
        copy.setNoEmployeeOrderContent(noEmployeeOrderContent);
        copy.setShortdescription(shortdescription);
        copy.setStandard(standard);
        copy.setUntilDate(untilDate);
        copy.setSign(sign);
        copy.setSuborder_customer(suborder_customer);
        
        if (copyroot) {
            copy.setSign("copy_of_" + sign);
        }
        
        for (Suborder child : children) {
            Suborder childCopy = child.copy(false, creator);
            childCopy.setParentorder(copy);
            copy.addSuborder(childCopy);
        }
        return copy;
    }
    
}
