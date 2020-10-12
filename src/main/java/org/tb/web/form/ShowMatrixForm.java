/*
 * File:          $RCSfile$
 * Version:       $Revision$
 * 
 * Created:       29.11.2006 by cb
 * Last changed:  $Date$ by $Author$
 * 
 * Copyright (C) 2006 by HBT GmbH, www.hbt.de
 *
 */
package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * @author cb
 * @since 29.11.2006
 */
public class ShowMatrixForm extends ActionForm {
	private static final long serialVersionUID = 1L; // -5141807789236654602L;
	private String fromDay;
    private String untilDay;
    private String fromMonth;
    private String untilMonth;
    private String fromYear;
    private String untilYear;
    private Long employeeContractId;

    private String order;
    private String suborder;

    private String matrixview;
    private long orderId;
    
    private Boolean invoice;
    private Boolean nonInvoice;

    public String getFromDay() {
        return fromDay;
    }

    public void setFromDay(String fromDay) {
        this.fromDay = fromDay;
    }

    public String getFromMonth() {
        return fromMonth;
    }

    public void setFromMonth(String fromMonth) {
        this.fromMonth = fromMonth;
    }

    public String getFromYear() {
        return fromYear;
    }

    public void setFromYear(String fromYear) {
        this.fromYear = fromYear;
    }

    public String getMatrixview() {
        return matrixview;
    }

    public void setMatrixview(String matrixview) {
        this.matrixview = matrixview;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getSuborder() {
        return suborder;
    }

    public void setSuborder(String suborder) {
        this.suborder = suborder;
    }

    public String getUntilDay() {
        return untilDay;
    }

    public void setUntilDay(String untilDay) {
        this.untilDay = untilDay;
    }

    public String getUntilMonth() {
        return untilMonth;
    }

    public void setUntilMonth(String untilMonth) {
        this.untilMonth = untilMonth;
    }

    public String getUntilYear() {
        return untilYear;
    }

    public void setUntilYear(String untilYear) {
        this.untilYear = untilYear;
    }

    /**
	 * @return the employeeContractId
	 */
	public Long getEmployeeContractId() {
		return employeeContractId;
	}

	/**
	 * @param employeeContractId the employeeContractId to set
	 */
	public void setEmployeeContractId(Long employeeContractId) {
		this.employeeContractId = employeeContractId;
	}

	@Override
    public void reset(ActionMapping arg0, HttpServletRequest arg1) {
        // TODO Auto-generated method stub
        super.reset(arg0, arg1);
        invoice = false;
        nonInvoice = false;
    }

    @Override
    public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1) {
        // TODO Auto-generated method stub
        return super.validate(arg0, arg1);
    }

	public boolean isInvoice() {
		return invoice;
	}

	public void setInvoice(boolean invoice) {
		this.invoice = invoice;
	}

	public boolean isNonInvoice() {
		return nonInvoice;
	}

	public void setNonInvoice(boolean nonInvoice) {
		this.nonInvoice = nonInvoice;
	}
}

/*
 $Log$
 */