package org.tb.dailyreport;

import static lombok.AccessLevel.PRIVATE;

import java.util.Comparator;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public class TimereportByOrderAscComparator implements Comparator<Timereport> {

    public static final Comparator<Timereport> INSTANCE = new TimereportByOrderAscComparator();

    /**
     * @param tr1 first {@link Timereport}
     * @param tr2 second {@link Timereport}
     * @return Returns -1, 0, 1 if the first {@link Timereport} is less, equal, greater than the second one.
     */
    public int compare(Timereport tr1, Timereport tr2) {
        if (tr1.getSuborder().getCustomerorder().getSign().compareTo(tr2.getSuborder().getCustomerorder().getSign()) < 0) {
            return -1;
        } else if (tr1.getSuborder().getCustomerorder().getSign().compareTo(tr2.getSuborder().getCustomerorder().getSign()) > 0) {
            return 1;
        } else {
            if (tr1.getSuborder().getSign().compareTo(tr2.getSuborder().getSign()) < 0) {
                return -1;
            } else if (tr1.getSuborder().getSign().compareTo(tr2.getSuborder().getSign()) > 0) {
                return 1;
            } else {
                if (tr1.getReferenceday().getRefdate().compareTo(tr2.getReferenceday().getRefdate()) < 0) {
                    return -1;
                } else if (tr1.getReferenceday().getRefdate().compareTo(tr2.getReferenceday().getRefdate()) > 0) {
                    return 1;
                } else {
                    if (tr1.getEmployeecontract().getEmployee().getSign().compareTo(tr2.getEmployeecontract().getEmployee().getSign()) < 0) {
                        return -1;
                    } else if (tr1.getEmployeecontract().getEmployee().getSign().compareTo(tr2.getEmployeecontract().getEmployee().getSign()) > 0) {
                        return 1;
                    }
                }
            }
        }
        return 0;
    }

}