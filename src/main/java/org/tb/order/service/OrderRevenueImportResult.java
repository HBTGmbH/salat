package org.tb.order.service;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderRevenueImportResult {
    private List<RowResult> rowResults;
    private int successCount;
    private int errorCount;

    @Data
    @Builder
    public static class RowResult {
        private int rowIndex;
        private String content;
        private List<String> errors;
        private boolean success;
    }
}
