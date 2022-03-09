package org.tb.order.domain.comparator;

import static lombok.AccessLevel.PRIVATE;

import java.util.Comparator;
import lombok.NoArgsConstructor;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;

@NoArgsConstructor(access = PRIVATE)
public class EmployeeOrderComparator implements Comparator<Employeeorder> {

    public static final Comparator<Employeeorder> INSTANCE = new EmployeeOrderComparator();

    /**
     * Compares {@link Employeeorder}s by the result of the compare methods of {@link Employee#getName()}, {@link Customerorder#getSign()},
     * {@link Suborder#getSign()} and {@link Employeeorder#getFromDate()}.
     *
     * @param employeeorder1 first {@link Employeeorder}
     * @param employeeorder2 second {@link Employeeorder}
     * @return Returns -1, 0, 1 if the first {@link Employeeorder} is less, equal, greater than the second one.
     */
    public int compare(Employeeorder employeeorder1, Employeeorder employeeorder2) {
        if (employeeorder1.getEmployeecontract().getEmployee().getName().
                compareTo(employeeorder2.getEmployeecontract().getEmployee().getName()) < 0) {
            return -1;
        } else if (employeeorder1.getEmployeecontract().getEmployee().getName().
                compareTo(employeeorder2.getEmployeecontract().getEmployee().getName()) > 0) {
            return 1;
        } else {
            if (employeeorder1.getSuborder().getCustomerorder().getSign().
                    compareTo(employeeorder2.getSuborder().getCustomerorder().getSign()) < 0) {
                return -1;
            } else if (employeeorder1.getSuborder().getCustomerorder().getSign().
                    compareTo(employeeorder2.getSuborder().getCustomerorder().getSign()) > 0) {
                return 1;
            } else {
                if (employeeorder1.getSuborder().getSign().
                        compareTo(employeeorder2.getSuborder().getSign()) < 0) {
                    return -1;
                } else if (employeeorder1.getSuborder().getSign().
                        compareTo(employeeorder2.getSuborder().getSign()) > 0) {
                    return 1;
                } else {
                    if (employeeorder1.getFromDate().
                            compareTo(employeeorder2.getFromDate()) < 0) {
                        return -1;
                    } else if (employeeorder1.getFromDate().
                            compareTo(employeeorder2.getFromDate()) > 0) {
                        return 1;
                    }
                }
            }
        }
        return 0;
    }

}
