package org.tb.order.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EmployeeOrderData {
    private final SuborderData suborder;
    private final long employeeorderId;
}
