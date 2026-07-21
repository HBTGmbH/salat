package org.tb.dailyreport.domain;

import java.time.LocalDate;
import java.util.List;
import org.tb.common.exception.ServiceFeedbackMessage;

public record ReleaseReviewDTO(
    long employeeContractId,
    LocalDate releaseMonth,
    List<ReleaseReviewDay> days,
    List<ServiceFeedbackMessage> errors
) {
    public boolean canRelease() {
        return errors.isEmpty();
    }
}
