package org.tb.common.web;

import java.util.Map;

/**
 * Contributes UI-state keys and their HTTP request-parameter mappings to {@link UiStateKey}.
 *
 * <p>Implement this interface in the module that owns a particular selection dimension and
 * annotate the implementation with {@code @Component}. {@link UiStateKey} collects all
 * contributors at startup and builds a single param-to-key map from their contributions.
 *
 * <p>Example (employee module):
 * <pre>{@code
 * @Component
 * public class EmployeeUiStateKeyContributor implements UiStateKeyContributor {
 *     public static final String SELECTED_CONTRACT = "selectedContract";
 *
 *     @Override
 *     public Map<String, String> getParamToKeyMappings() {
 *         return Map.of("employeeContractId", SELECTED_CONTRACT);
 *     }
 * }
 * }</pre>
 */
public interface UiStateKeyContributor {

    /**
     * Returns a map of HTTP request-parameter name to internal UI-state key.
     * Each entry tells {@link UiStateFilter} which request parameter should populate which key
     * in {@link UiState}.
     */
    Map<String, String> getParamToKeyMappings();
}
