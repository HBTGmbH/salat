package org.tb.order.action;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

@Getter
@Setter
public class GenerateMultipleEmployeeordersForm extends ActionForm {
    private static final long serialVersionUID = 1L;

    private String[] employeecontractIdArray;
    private Long customerOrderId;
    private Long suborderId;
    private Boolean showOnlyValid;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        setCustomerOrderId(-1L);
        setSuborderId(-1L);
        setShowOnlyValid(false);
    }

    @Nonnull
    public Boolean getShowOnlyValid() {
        return showOnlyValid != null && showOnlyValid;
    }

}
