package org.tb.budget.controller;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * The evaluated window of the single order view.
 *
 * <p>The order itself is not part of it: it is a remembered filter value and arrives as
 * {@code fCustomerOrderSign} through the UiState mapping (ADR-0022), a namespace no form field may
 * enter. The two dates are not remembered — they belong to the one evaluation somebody asked for,
 * not to a selection that travels from page to page.
 */
@Getter
@Setter
public class ControllingFilterForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate until;

}
