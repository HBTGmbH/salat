package org.tb.web.form;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;

/**
 * Form for adding a suborder
 * 
 * @author oda
 *
 */
public class AddSuborderForm extends ActionForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4415693005261710055L;
	private long id;
	private String sign;
	private String description;
	private String shortdescription;
	private String invoice;
	private String currency;
	private Double hourlyRate;
	
	private long customerorderId;
	
	
	private String action;
	private Boolean standard;
	private Boolean commentnecessary;
	
	private String validFrom;
	private String validUntil;
	private Double debithours;
	private Byte debithoursunit;
	private Boolean hide;
	private Long parentId;
	private String parentDescriptionAndSign;
	private Boolean noEmployeeOrderContent;

	
	public String getParentDescriptionAndSign() {
		return parentDescriptionAndSign;
	}

	public void setParentDescriptionAndSign(String parentDescriptionAndSign) {
		this.parentDescriptionAndSign = parentDescriptionAndSign;
	}

	public Long getParentId() {
		return parentId;
	}

	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public long getCustomerorderId() {
		return customerorderId;
	}

	public void setCustomerorderId(long customerorderId) {
		this.customerorderId = customerorderId;
	}

	public Double getHourlyRate() {
		return hourlyRate;
	}

	public void setHourlyRate(Double hourlyRate) {
		this.hourlyRate = hourlyRate;
	}
	
	public String getSign() {
		return sign;
	}

	public void setSign(String sign) {
		this.sign = sign;
	}
	
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getInvoice() {
		return invoice;
	}

	public void setInvoice(String invoice) {
		this.invoice = invoice;
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
	
	public String getShortdescription() {
		return shortdescription;
	}

	public void setShortdescription(String shortdescription) {
		this.shortdescription = shortdescription;
	}

	public Boolean getCommentnecessary() {
		return commentnecessary;
	}

	public void setCommentnecessary(Boolean commentnecessary) {
		this.commentnecessary = commentnecessary;
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
		return hide;
	}

	/**
	 * @param hide the hide to set
	 */
	public void setHide(Boolean hide) {
		this.hide = hide;
	}

	/**
	 * @return the validFrom
	 */
	public String getValidFrom() {
		return validFrom;
	}

	/**
	 * @param validFrom the validFrom to set
	 */
	public void setValidFrom(String validFrom) {
		this.validFrom = validFrom;
	}

	/**
	 * @return the validUntil
	 */
	public String getValidUntil() {
		return validUntil;
	}

	/**
	 * @param validUntil the validUntil to set
	 */
	public void setValidUntil(String validUntil) {
		this.validUntil = validUntil;
	}

	/**
	 * @return the noEmployeeOrderContent
	 */
	public Boolean getNoEmployeeOrderContent() {
		return noEmployeeOrderContent;
	}

	/**
	 * @param noEmployeeOrderContent the noEmployeeOrderContent to set
	 */
	public void setNoEmployeeOrderContent(Boolean noEmployeeOrderContent) {
		this.noEmployeeOrderContent = noEmployeeOrderContent;
	}

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {	
		sign = "";
		description = "";
		shortdescription = "";
		invoice = "J";
		currency = GlobalConstants.DEFAULT_CURRENCY;
		standard = false;
		commentnecessary = false;
		
		Date now = new Date();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		validFrom = simpleDateFormat.format(now);
		validUntil = simpleDateFormat.format(now);
		debithours = null;
		debithoursunit = null;
		hide=false;
		noEmployeeOrderContent=false;
		
//		hourlyRate = 0.0;
//		request.getSession().setAttribute("hourlyRate", new Double(0.0));
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();

		// actually, no checks here
		return errors;
	}

}
