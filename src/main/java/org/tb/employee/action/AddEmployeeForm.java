package org.tb.employee.action;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for adding an employee
 *
 * @author oda
 */
@Getter
@Setter
public class AddEmployeeForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -222338996719117592L;

    private long id;
    private String firstname;
    private String lastname;
    private String loginname;
    private String password;
    private String sign;
    private String status;
    private String gender;
    private String action;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        firstname = "";
        lastname = "";
        loginname = "";
        password = "";
        status = "";
        sign = "";
        gender = "M";
    }

}
