package org.tb.order.action;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;

/**
 * Form for adding an employee order
 *
 * @author oda
 */
@Getter
@Setter
@Slf4j
public class AddEmployeeOrderForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -2418121509861779749L;

    private long id;
    private String sign;
    private String validFrom;
    private String validUntil;
    private String debithours;
    private Byte debithoursunit;
    private String status;
    private String order;
    private String suborder;
    private Boolean showOnlyValid;
    private long orderId;
    private long suborderId;
    private Long employeeContractId;
    private String action;

    public void reset(ActionMapping mapping, HttpServletRequest request, boolean extraCall) {
        Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if (currentEmployeeContract != null) {
            employeeContractId = currentEmployeeContract.getId();
        } else {
            try {
                Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
                employeeContractId = loginEmployeeContract.getId();
            } catch (Exception e) {
                log.error("reset threw exception.", e);
            }
        }

        sign = "";
        status = "";
        validFrom = DateUtils.format(DateUtils.today()); // 'yyyy-mm-dd'
        validUntil = DateUtils.format(DateUtils.today()); // 'yyyy-mm-dd'
        debithours = null;
        debithoursunit = null;
        if (!extraCall) {
            showOnlyValid = false;
        }
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        reset(mapping, request, false);
    }

    public void useDatesFromCustomerOrder(Customerorder customerorder) {
        if (customerorder == null) {
            return;
        }
        LocalDate coFromDate = customerorder.getFromDate();
        LocalDate coUntilDate = customerorder.getUntilDate();
        String coFromDateString = DateUtils.format(coFromDate);
        String coUntilDateString;
        if (coUntilDate != null) {
            coUntilDateString = DateUtils.format(coUntilDate);
        } else {
            coUntilDateString = "";
        }
        setValidFrom(coFromDateString);
        setValidUntil(coUntilDateString);
    }

    @Nonnull
    public Boolean getShowOnlyValid() {
        return showOnlyValid != null && showOnlyValid;
    }

}
