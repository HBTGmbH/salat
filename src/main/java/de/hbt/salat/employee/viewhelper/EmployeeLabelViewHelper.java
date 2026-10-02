package de.hbt.salat.employee.viewhelper;

import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * How a person and a contract are named in a select (#1266, → ADR-0017): {@code Vorname Nachname |
 * Kürzel}. The name comes first because that is what one recognises while scanning a list; the sign
 * behind it tells two people of the same name apart and makes the person findable by sign, since the
 * select searches the text of the option.
 *
 * <p>A contract is named like its person. Its period goes underneath as {@code data-subtext}, where
 * the select shows it as a second line and searches it too.
 *
 * <p>Used from templates as {@code ${@employeeLabelViewHelper.label(e)}}; the hidden marker is
 * appended there as for every other select ({@code HiddenMarkerViewHelper}). Java code that only has
 * name and sign — a record from a query — calls {@link #of(String, String)}.
 */
@Component
public class EmployeeLabelViewHelper {

    /** The open end of a contract, wherever its period is shown. */
    static final String OPEN_END = "∞";

    /** As dates read everywhere else on the pages, not the ISO form of {@code DateUtils.format}. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public String label(Employee employee) {
        return employee == null ? null : of(employee.getName(), employee.getSign());
    }

    /** For a record that carries name and sign rather than the person. */
    public String label(String name, String sign) {
        return of(name, sign);
    }

    public String contractLabel(Employeecontract employeecontract) {
        return employeecontract == null ? null : label(employeecontract.getEmployee());
    }

    /** {@code dd.MM.yyyy – dd.MM.yyyy}, or {@code dd.MM.yyyy – ∞} for an open end. */
    public String contractPeriod(Employeecontract employeecontract) {
        return period(employeecontract);
    }

    /** Name and sign, falling back to whichever of the two is there. */
    public static String of(String name, String sign) {
        var hasName = name != null && !name.isBlank();
        var hasSign = sign != null && !sign.isBlank();
        if (hasName && hasSign) {
            return name.trim() + " | " + sign.trim();
        }
        return hasName ? name.trim() : hasSign ? sign.trim() : "";
    }

    /**
     * The contract as a page names the one it shows: the label of the selection with its period, so
     * that heading and selection read the same.
     */
    public static String title(Employeecontract employeecontract) {
        var employee = employeecontract.getEmployee();
        return of(employee.getName(), employee.getSign()) + " (" + period(employeecontract) + ")";
    }

    private static String period(Employeecontract employeecontract) {
        if (employeecontract == null || employeecontract.getValidFrom() == null) {
            return null;
        }
        var until = employeecontract.getValidUntil();
        return DATE.format(employeecontract.getValidFrom()) + " – " + (until == null ? OPEN_END : DATE.format(until));
    }

}
