package org.tb.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;

@Getter
@Setter
public class ShowBudgetForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 1194028167113461984L;

    private int text;
    private Long customerorderId = -1L;
    private Long customerOrSuborderId = -1L;

}
