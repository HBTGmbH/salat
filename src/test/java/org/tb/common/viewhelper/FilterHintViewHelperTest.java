package org.tb.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tb.common.viewhelper.FilterHintViewHelper.TOAST_SUCCESS;
import static org.tb.common.viewhelper.FilterHintViewHelper.TOAST_SUCCESS_HINT;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKey;

class FilterHintViewHelperTest {

    private static final UiStateKey FILTER = new UiStateKey("list.Filter");
    private static final UiStateKey CUSTOMER = new UiStateKey("customer.Id");
    private static final String HINT = "The entry may not be shown.";

    private UiState uiState;
    private RedirectAttributesModelMap redirectAttributes;
    private FilterHintViewHelper viewHelper;

    @BeforeEach
    void setUp() {
        uiState = new UiState();
        redirectAttributes = new RedirectAttributesModelMap();
        var messages = mock(MessageSourceAccessor.class);
        when(messages.getMessage(anyString(), anyString())).thenReturn(HINT);
        viewHelper = new FilterHintViewHelper(uiState, messages);
    }

    @Test
    void withoutAFilterOnlyTheMessageIsAnnounced() {
        viewHelper.addSuccess(redirectAttributes, "Saved.", FILTER, CUSTOMER);

        assertThat(flash())
            .containsEntry(TOAST_SUCCESS, "Saved.")
            .doesNotContainKey(TOAST_SUCCESS_HINT);
    }

    /** The hint travels on its own so the toast can give it a line of its own. */
    @Test
    void aSetFilterAddsTheHintAsItsOwnAttribute() {
        uiState.setValue(CUSTOMER, "42");

        viewHelper.addSuccess(redirectAttributes, "Saved.", FILTER, CUSTOMER);

        assertThat(flash())
            .containsEntry(TOAST_SUCCESS, "Saved.")
            .containsEntry(TOAST_SUCCESS_HINT, HINT);
    }

    /** An empty filter is a cleared one — it hides nothing and is no reason for a hint. */
    @Test
    void anEmptyFilterIsNoFilter() {
        uiState.setValue(FILTER, "  ");

        viewHelper.addSuccess(redirectAttributes, "Saved.", FILTER, CUSTOMER);

        assertThat(flash()).doesNotContainKey(TOAST_SUCCESS_HINT);
    }

    /** A filter of another list says nothing about this one. */
    @Test
    void onlyTheNamedFiltersCount() {
        uiState.setValue(new UiStateKey("other.Filter"), "abc");

        viewHelper.addSuccess(redirectAttributes, "Saved.", FILTER, CUSTOMER);

        assertThat(flash()).doesNotContainKey(TOAST_SUCCESS_HINT);
    }

    private Map<String, Object> flash() {
        return new HashMap<>(redirectAttributes.getFlashAttributes());
    }
}
