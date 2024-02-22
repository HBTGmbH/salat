package org.tb.reporting.action;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ExecuteReportForm extends ActionForm {

    private long reportId;
    private List<ReportParameter> parameters = new ArrayList<>();

    @Getter
    @Setter
    public static class ReportParameter {

        private String name;
        private String type;
        private String value;

    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        reportId = -1;
        parameters = new ArrayList<>();
        // get rid of Index 0 out of bounds and IllegalArgumentException: No bean specified
        // this is due to struts and array input mapping
        for (int i = 0; i < 20; i++) {
            parameters.add(new ReportParameter());
        }
    }

}
