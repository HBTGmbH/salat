package de.hbt.salat.budget.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.annotation.NumberFormat.Style;

@Getter
@Setter
public class OrderPricingForm {

    private Long id;
    private Long customerorderId;
    private String suborderSign;
    /** Optional: without it the rate applies to everyone on the order. */
    private Long employeeId;

    /** Optional (#1065): without it the rate applies whatever plan a booking belongs to. */
    private Long orderBudgetId;

    private String description;
    @NumberFormat(style = Style.NUMBER)
    private BigDecimal priceEuro;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;

    public boolean isNew() {
        return id == null;
    }

}
