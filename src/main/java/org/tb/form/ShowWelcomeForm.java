package org.tb.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;

@Getter
@Setter
public class ShowWelcomeForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -1868543616734155005L;

    private Long employeeContractId;

}
