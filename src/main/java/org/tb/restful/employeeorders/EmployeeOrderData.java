package org.tb.restful.employeeorders;

import lombok.Getter;
import lombok.Setter;
import org.tb.restful.suborders.SuborderData;

@Getter
@Setter
public class EmployeeOrderData {
    private SuborderData suborder;
    private long employeeorderId;
}
