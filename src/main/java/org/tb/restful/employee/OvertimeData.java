package org.tb.restful.employee;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OvertimeData {
    private String overtime;
    private String monthlyOvertime;
}
