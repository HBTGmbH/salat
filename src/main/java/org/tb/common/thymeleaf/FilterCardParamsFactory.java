package org.tb.common.thymeleaf;

import org.springframework.stereotype.Component;

@Component("filterCardParams")
public class FilterCardParamsFactory {

    /** No HTMX, with advanced-filter toggle (default). */
    public FilterCardParams basic(String formKey) {
        return FilterCardParams.builder().formKey(formKey).build();
    }

    /** With HTMX partial updates, with advanced-filter toggle. */
    public FilterCardParams htmx(String formKey, String hxTarget) {
        return FilterCardParams.builder().formKey(formKey).hxTarget(hxTarget).build();
    }

    /** No HTMX, no advanced-filter toggle (always-visible primary filters only). */
    public FilterCardParams simple(String formKey) {
        return FilterCardParams.builder().formKey(formKey).showAdvancedToggle(false).build();
    }
}
