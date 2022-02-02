package org.tb.restful.employee;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
public class OvertimeData {
    final private String overtime;
    final private String monthlyOvertime;
}
