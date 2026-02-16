package org.tb.order.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRevenueImportRow {
    private String orderSign;
    private LocalDate date;
    private String type;
    private BigDecimal amount;
    private String originalRowContent;
    private int rowIndex;
}
