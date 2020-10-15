package org.tb.restful.bookings;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
    private double costs;

}
