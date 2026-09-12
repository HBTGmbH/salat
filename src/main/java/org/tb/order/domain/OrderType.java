package org.tb.order.domain;

import lombok.Getter;

@Getter
public enum OrderType {

    STANDARD("main.customerorder.orderType.standard"),
    KRANK_URLAUB_ABWESEND("main.customerorder.orderType.krankUrlaubAbwesend"),
    BEREITSCHAFT("main.customerorder.orderType.bereitschaft")
    ;

    private final String label;

    OrderType(String label) {
        this.label = label;
    }

    /**
     * Whether time booked on an order of this type is working time (#463).
     *
     * <p>Standby is time an employee is available, not time worked, so it is left out of every
     * sum of working time — the daily and monthly totals as well as the overtime account.
     * Sickness, vacation and absence stay working time: they are what the daily working time
     * would otherwise have been spent on.
     */
    public boolean isWorkingTime() {
        return this != BEREITSCHAFT;
    }

}
