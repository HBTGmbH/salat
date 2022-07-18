package org.tb.reporting.action;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;

@Getter
@Setter
public class CreateEditDeleteReportForm extends ActionForm {

    private long reportId;

}
