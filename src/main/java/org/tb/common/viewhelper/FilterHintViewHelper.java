package org.tb.common.viewhelper;

import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKey;

/**
 * Saving leaves the filter of the list untouched (ADR-0023) — a filter is the user's setting, and
 * an entry somebody added is no reason to change it. The price is that the list the user lands on
 * may not show what was just saved, so the success message says so where a filter is set.
 *
 * <p>The hint travels as a flash attribute of its own and the toast gives it its own line: it says
 * something about the list, not about the saving, and reading it as part of the confirmation would
 * be misleading.
 *
 * <p>Only the filters that can <em>exclude</em> an entry are worth naming: a search text, a chosen
 * customer, a chosen order. The "show hidden" and "show expired" switches only ever widen a list
 * and can therefore never be the reason something is missing.
 */
@Component
@RequiredArgsConstructor
public class FilterHintViewHelper {

    static final String TOAST_SUCCESS = "toastSuccess";
    static final String TOAST_SUCCESS_HINT = "toastSuccessHint";

    private final UiState uiState;
    private final MessageSourceAccessor messages;

    /**
     * Announces a successful save, plus the hint where one of the given filters is set.
     */
    public void addSuccess(RedirectAttributes redirectAttributes, String message, UiStateKey... filterKeys) {
        redirectAttributes.addFlashAttribute(TOAST_SUCCESS, message);
        if (anyFilterSet(filterKeys)) {
            redirectAttributes.addFlashAttribute(TOAST_SUCCESS_HINT,
                messages.getMessage("main.general.message.filtered.hint",
                    "The entry may not be listed because a filter is set."));
        }
    }

    private boolean anyFilterSet(UiStateKey... filterKeys) {
        for (UiStateKey key : filterKeys) {
            var value = uiState.getValue(key);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
