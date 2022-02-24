package org.tb.employee;

import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for showing all employees.
 * Actually not used - will be needed if we want to have an editable employees display
 * (like the timereport daily display)
 *
 * @author oda
 */
@Getter
@Setter
public class ShowEmployeeForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -3316717791855254L;

    private String filter;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        filter = "";
    }

}
