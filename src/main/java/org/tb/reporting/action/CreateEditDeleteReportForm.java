package org.tb.reporting.action;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;

@Getter
@Setter
public class CreateEditDeleteReportForm extends ActionForm {

    private String mode; // create, edit
    private long reportId;
    private String name;
    private String sql;

}
