package org.tb.order.domain;

import lombok.Getter;

@Getter
public enum OrderType {
    KUNDE("main.customerorder.orderType.kunde"),
    KRANK("main.customerorder.orderType.krank"),
    URLAUB("main.customerorder.orderType.urlaub"),
    S_URLAUB("main.customerorder.orderType.surlaub"),
    RESTURLAUB("main.customerorder.orderType.resturlaub");

    private final String label;

    OrderType(String label) {
        this.label = label;
    }

    public static OrderType fromLabel(String label) {
        for (OrderType type : values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        return null;
    }

    public boolean isRelevantForWorkingTimeValidation() {
        return switch (this) {
            case KUNDE -> true;
            case KRANK, URLAUB, S_URLAUB, RESTURLAUB -> false;
        };
    }
}
