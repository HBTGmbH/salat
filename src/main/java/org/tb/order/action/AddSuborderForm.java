package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.order.domain.OrderType;

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
    private char invoice;
    private long customerorderId;
    private String action;
    private Boolean standard;
    private Boolean commentnecessary;
    private Boolean fixedPrice;
    private Boolean trainingFlag;
    private String validFrom;
    private String validUntil;
    private String debithours;
    private Byte debithoursunit;
    private Boolean hide;
    private Long parentId;
    private String parentDescriptionAndSign;
    private String orderTypeString;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        sign = "";
        description = "";
        shortdescription = "";
        suborder_customer = "";
        invoice = GlobalConstants.INVOICE_YES;
        standard = false;
        commentnecessary = false;
        fixedPrice = false;
        trainingFlag = false;

        validFrom = DateUtils.format(DateUtils.today());
        validUntil = validFrom;
        debithours = null;
        debithoursunit = null;
        hide = false;

        setOrderType(null);
    }

    public OrderType getOrderType() {
        if(orderTypeString == null || orderTypeString.isBlank()) {
            return null;
        }
        return OrderType.valueOf(orderTypeString);
    }

    public void setOrderType(OrderType orderType) {
        if(orderType == null) {
            this.orderTypeString = null;
        } else {
            this.orderTypeString = orderType.name();
        }
    }

}
