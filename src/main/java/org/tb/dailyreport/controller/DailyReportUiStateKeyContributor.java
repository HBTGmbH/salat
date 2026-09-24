package org.tb.dailyreport.controller;

import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

import java.util.Map;

import static java.util.Map.of;

@Component
public class DailyReportUiStateKeyContributor implements UiStateKeyContributor {

    public static final UiStateKey EMPLOYEE_CONTRACT_ID = new UiStateKey("employeeContract.Id");
    public static final UiStateKey ACCEPTANCE_SUPERVISOR_ID = new UiStateKey("acceptance.Supervisor.Id");
    public static final UiStateKey ACCEPTANCE_EMPLOYEE_CONTRACT_ID = new UiStateKey("acceptance.EmployeeContract.Id");
    public static final UiStateKey MATRIX_YEAR = new UiStateKey("matrix.Year");
    public static final UiStateKey MATRIX_MONTH = new UiStateKey("matrix.Month");

    /**
     * The filter of the booking list (#1092). Every selection travels as one string — the multi-valued ones comma
     * separated — because UiState remembers one value per key, and a filter that is forgotten as soon as somebody
     * opens a booking would be no filter at all.
     */
    public static final UiStateKey BOOKINGS_EMPLOYEES = new UiStateKey("bookings.Employees");
    public static final UiStateKey BOOKINGS_CUSTOMERS = new UiStateKey("bookings.Customers");
    public static final UiStateKey BOOKINGS_ORDERS = new UiStateKey("bookings.Orders");
    public static final UiStateKey BOOKINGS_SUBORDERS = new UiStateKey("bookings.Suborders");
    public static final UiStateKey BOOKINGS_TICKETS = new UiStateKey("bookings.Tickets");
    public static final UiStateKey BOOKINGS_TICKET_CHILDREN = new UiStateKey("bookings.TicketChildren");
    public static final UiStateKey BOOKINGS_FROM = new UiStateKey("bookings.From");
    public static final UiStateKey BOOKINGS_UNTIL = new UiStateKey("bookings.Until");
    public static final UiStateKey BOOKINGS_BILLABLE = new UiStateKey("bookings.Billable");
    public static final UiStateKey BOOKINGS_SORT = new UiStateKey("bookings.Sort");
    public static final UiStateKey BOOKINGS_LIMIT = new UiStateKey("bookings.Limit");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.ofEntries(
            Map.entry("fEmployeeContractId", EMPLOYEE_CONTRACT_ID),
            Map.entry("fAcceptanceSupervisorId", ACCEPTANCE_SUPERVISOR_ID),
            Map.entry("fAcceptanceEmployeeContractId", ACCEPTANCE_EMPLOYEE_CONTRACT_ID),
            Map.entry("fYear", MATRIX_YEAR),
            Map.entry("fMonth", MATRIX_MONTH),
            Map.entry("fBookingsEmployees", BOOKINGS_EMPLOYEES),
            Map.entry("fBookingsCustomers", BOOKINGS_CUSTOMERS),
            Map.entry("fBookingsOrders", BOOKINGS_ORDERS),
            Map.entry("fBookingsSuborders", BOOKINGS_SUBORDERS),
            Map.entry("fBookingsTickets", BOOKINGS_TICKETS),
            Map.entry("fBookingsTicketChildren", BOOKINGS_TICKET_CHILDREN),
            Map.entry("fBookingsFrom", BOOKINGS_FROM),
            Map.entry("fBookingsUntil", BOOKINGS_UNTIL),
            Map.entry("fBookingsBillable", BOOKINGS_BILLABLE),
            Map.entry("fBookingsSort", BOOKINGS_SORT),
            Map.entry("fBookingsLimit", BOOKINGS_LIMIT)
        );
    }
}
