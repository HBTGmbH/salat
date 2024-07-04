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

}
