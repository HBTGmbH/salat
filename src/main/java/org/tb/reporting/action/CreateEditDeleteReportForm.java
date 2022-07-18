package org.tb.reporting.action;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

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
