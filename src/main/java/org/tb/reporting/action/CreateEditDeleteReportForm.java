package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

@Getter
@Setter
public class CreateEditDeleteReportForm extends ActionForm {

    private String mode; // create, edit
    private long reportId;
    private String name;
    private String sql;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        reportId = -1;
        mode = "";
        name = "";
        sql = "";
    }
}
