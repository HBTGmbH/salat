package org.tb.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;

import javax.servlet.http.HttpServletRequest;

@Getter
@Setter
public class AddEmployeeOrderContentForm extends ActionForm {
    static final long serialVersionUID = 1L; // 1L;

    private String description;
    private String task;
    private String boundary;
    private String procedure;
    private Integer qm_process_id;
    private String contact_contract_customer;
    private String contact_tech_customer;
    private Long contact_contract_hbt_emp_id;
    private Long contact_tech_hbt_emp_id;
    private String additional_risks;
    private String arrangement;

    @Override
    public void reset(ActionMapping arg0, HttpServletRequest arg1) {
        description = "";
        task = "";
        boundary = "";
        procedure = "";
        qm_process_id = GlobalConstants.QM_PROCESS_ID_OTHER;
        contact_contract_customer = "";
        contact_tech_customer = "";
        contact_contract_hbt_emp_id = null;
        contact_tech_hbt_emp_id = null;
        additional_risks = "";
        arrangement = "";
    }

}
