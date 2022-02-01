package org.tb.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Form for adding a suborder
 *
 * @author oda
 */
@Getter
@Setter
public class AddSuborderForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -4415693005261710055L;

    private long id;
    private String sign;
    private String description;
    private String shortdescription;
    private String suborder_customer;
    private String invoice;
    private String currency;
    private Double hourlyRate;
    private long customerorderId;
    private String action;
    private Boolean standard;
    private Boolean commentnecessary;
    private Boolean fixedPrice;
    private Boolean trainingFlag;
    private String validFrom;
    private String validUntil;
    private Double debithours;
    private Byte debithoursunit;
    private Boolean hide;
    private Long parentId;
    private String parentDescriptionAndSign;
    private Boolean noEmployeeOrderContent;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        sign = "";
        description = "";
        shortdescription = "";
        suborder_customer = "";
        invoice = "J";
        currency = GlobalConstants.DEFAULT_CURRENCY;
        standard = false;
        commentnecessary = false;
        fixedPrice = false;
        trainingFlag = false;

        Date now = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        validFrom = simpleDateFormat.format(now);
        validUntil = simpleDateFormat.format(now);
        debithours = null;
        debithoursunit = null;
        hide = false;
        noEmployeeOrderContent = false;

    }

}
