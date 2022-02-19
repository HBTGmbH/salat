package org.tb.restful.employeeorders;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.tb.restful.suborders.SuborderData;

@Getter
@RequiredArgsConstructor
public class EmployeeOrderData {
    private final SuborderData suborder;
    private final long employeeorderId;
}
