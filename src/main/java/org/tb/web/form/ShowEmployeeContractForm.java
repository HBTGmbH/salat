package org.tb.web.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for showing all employee contracts.
 * Actually not used - will be needed if we want to have an editable employee contracts display
 * (like the timereport daily display)
 *
 * @author oda
 */
@Getter
@Setter
public class ShowEmployeeContractForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -7114415176299026774L;

    private String filter;
    private Boolean show;
    private Long employeeId;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        filter = "";
        show = false;
        employeeId = -1L;
    }

}
