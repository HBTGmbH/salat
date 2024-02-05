package org.tb.order.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
public class EmployeeOrderData {
    private final SuborderData suborder;
    private final long employeeorderId;
}
