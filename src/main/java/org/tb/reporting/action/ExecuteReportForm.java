package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.reporting.domain.ReportParameter;

@Getter
@Setter
public class ExecuteReportForm extends ActionForm {

    private long reportId;
    private List<ReportParameter> parameters = new ArrayList<>();

    public void initParameters(List<ReportParameter> initParameters, Set<String> additionalParameterNames) {
        parameters.clear();
        parameters.addAll(initParameters);
        for (var parameterName : additionalParameterNames) {
            if(parameters.stream().anyMatch(p -> p.getName().equals(parameterName))) continue;
            parameters.add(ReportParameter.builder()
                    .name(parameterName)
                    .type(getType(parameterName))
                    .value(getValue(parameterName))
                    .build()
            );
        }
        // get rid of Index X out of bounds and IllegalArgumentException: No bean specified
        // this is due to struts and array input mapping
        for (int i = parameters.size(); i < 20; i++) {
            parameters.add(ReportParameter.builder().build());
        }
    }

    private String getType(String parameterName) {
        if(Set.of("jahr", "monat", "year", "month").contains(parameterName.toLowerCase())) {
            return "number";
        }
        if(Set.of("datum", "date", "from", "to", "von", "bis").contains(parameterName.toLowerCase())) {
            return "date";
        }
        return "string";
    }

    private String getValue(String parameterName) {
        if(Set.of("jahr", "year").contains(parameterName.toLowerCase())) {
            return Year.now().toString();
        }
        if(Set.of("monat", "month").contains(parameterName.toLowerCase())) {
            return Integer.toString(YearMonth.now().getMonthValue());
        }
        if(Set.of("datum", "date", "from", "to", "von", "bis").contains(parameterName.toLowerCase())) {
            return LocalDate.now().toString();
        }
        return "";
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        reportId = -1;
        parameters = new ArrayList<>();
        // get rid of Index X out of bounds and IllegalArgumentException: No bean specified
        // this is due to struts and array input mapping
        for (int i = 0; i < 20; i++) {
            parameters.add(ReportParameter.builder().build());
        }
    }

}
