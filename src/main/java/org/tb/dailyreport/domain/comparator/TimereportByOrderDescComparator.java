package org.tb.dailyreport.domain.comparator;

import static lombok.AccessLevel.PRIVATE;

import java.util.Comparator;
import lombok.NoArgsConstructor;
import org.tb.dailyreport.domain.TimereportDTO;

@NoArgsConstructor(access = PRIVATE)
public class TimereportByOrderDescComparator implements Comparator<TimereportDTO> {

    public static final Comparator<TimereportDTO> INSTANCE = new TimereportByOrderDescComparator();

    /**
     * @param tr1 first {@link TimereportDTO}
     * @param tr2 second {@link TimereportDTO}
     * @return Returns -1, 0, 1 if the first {@link TimereportDTO} is less, equal, greater than the second one.
     */
    public int compare(TimereportDTO tr1, TimereportDTO tr2) {
        if (tr1.getCustomerorderSign().compareTo(tr2.getCustomerorderSign()) < 0) {
            return 1;
        } else if (tr1.getCustomerorderSign().compareTo(tr2.getCustomerorderSign()) > 0) {
            return -1;
        } else {
            if (tr1.getCompleteOrderSign().compareTo(tr2.getCompleteOrderSign()) < 0) {
                return 1;
            } else if (tr1.getCompleteOrderSign().compareTo(tr2.getCompleteOrderSign()) > 0) {
                return -1;
            } else {
                if (tr1.getReferenceday().compareTo(tr2.getReferenceday()) < 0) {
                    return 1;
                } else if (tr1.getReferenceday().compareTo(tr2.getReferenceday()) > 0) {
                    return -1;
                } else {
                    return tr2.getSequencenumber() - tr1.getSequencenumber();
                }
            }
        }
    }

}
