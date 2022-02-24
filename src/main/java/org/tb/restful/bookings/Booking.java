package org.tb.restful.bookings;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Builder
public class Booking {

    private long employeeorderId;
    private String orderLabel;
    private String suborderLabel;
    private int hours;
    private int minutes;
    private String comment;
    private boolean isTraining;
    private String suborderSign;
    private String orderSign;
    private String date;

}
