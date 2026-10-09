package de.hbt.salat.dailyreport.rest;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * The employee contract valid on each day of a period, for the lists that read a period of the
 * user's own bookings (#1450).
 *
 * <p>A period may span a change of contract, and the bookings after the change belong to the new
 * contract. Resolving the contract once, on the first day, returned the old contract's bookings only
 * and dropped the rest without a word; a period starting before the first contract answered 404 even
 * where it reached into that contract. So the contract is resolved day by day: a day without one
 * contributes nothing, and only a period without any answers 404 — with a reason, since a script
 * reading years in chunks cannot tell otherwise why it stopped.
 */
final class EmployeecontractsByDay {

    private EmployeecontractsByDay() {
    }

    /**
     * @return the id of the contract valid on each day that has one, in order of the days
     * @throws ResponseStatusException 404 if no day of the period has a contract
     */
    static Map<LocalDate, Long> resolve(LocalDate startDay, int days, Function<LocalDate, Employeecontract> contractValidAt) {
        var contractIdsByDay = new LinkedHashMap<LocalDate, Long>();
        for (int offset = 0; offset < days; offset++) {
            var day = DateUtils.addDays(startDay, offset);
            var employeecontract = contractValidAt.apply(day);
            if (employeecontract != null) {
                contractIdsByDay.put(day, employeecontract.getId());
            }
        }
        if (contractIdsByDay.isEmpty()) {
            var lastDay = DateUtils.addDays(startDay, days - 1);
            throw new ResponseStatusException(NOT_FOUND,
                    "No employee contract of the user is valid between " + startDay + " and " + lastDay);
        }
        return contractIdsByDay;
    }

}
