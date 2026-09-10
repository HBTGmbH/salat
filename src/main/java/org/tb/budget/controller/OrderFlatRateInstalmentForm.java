package org.tb.budget.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.annotation.NumberFormat.Style;

@Getter
@Setter
public class OrderFlatRateInstalmentForm {

    @NumberFormat(style = Style.NUMBER)
    private BigDecimal amountEuro;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate due;

    private String comment;

}
