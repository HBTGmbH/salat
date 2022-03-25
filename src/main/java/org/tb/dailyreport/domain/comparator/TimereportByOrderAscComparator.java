package org.tb.dailyreport.domain.comparator;

import static lombok.AccessLevel.PRIVATE;

import java.util.Comparator;
import lombok.NoArgsConstructor;
import org.tb.dailyreport.domain.TimereportDTO;

@NoArgsConstructor(access = PRIVATE)
public class TimereportByOrderAscComparator implements Comparator<TimereportDTO> {

    public static final Comparator<TimereportDTO> INSTANCE = new TimereportByOrderAscComparator();

    /**
     * @param tr1 first {@link TimereportDTO}
     * @param tr2 second {@link TimereportDTO}
     * @return Returns -1, 0, 1 if the first {@link TimereportDTO} is less, equal, greater than the second one.
     */
    public int compare(TimereportDTO tr1, TimereportDTO tr2) {
        if (tr1.getCustomerorderSign().compareTo(tr2.getCustomerorderSign()) < 0) {
            return -1;
        } else if (tr1.getCustomerorderSign().compareTo(tr2.getCustomerorderSign()) > 0) {
            return 1;
        } else {
            if (tr1.getSuborderSign().compareTo(tr2.getSuborderSign()) < 0) {
                return -1;
            } else if (tr1.getSuborderSign().compareTo(tr2.getSuborderSign()) > 0) {
                return 1;
            } else {
                if (tr1.getReferenceday().compareTo(tr2.getReferenceday()) < 0) {
                    return -1;
                } else if (tr1.getReferenceday().compareTo(tr2.getReferenceday()) > 0) {
                    return 1;
                } else {
                    if (tr1.getEmployeeSign().compareTo(tr2.getEmployeeSign()) < 0) {
                        return -1;
                    } else if (tr1.getEmployeeSign().compareTo(tr2.getEmployeeSign()) > 0) {
                        return 1;
                    }
                }
            }
        }
        return 0;
    }

}
