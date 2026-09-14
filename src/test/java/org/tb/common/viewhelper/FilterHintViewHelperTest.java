package org.tb.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKey;

class FilterHintViewHelperTest {

    private static final UiStateKey FILTER = new UiStateKey("list.Filter");
    private static final UiStateKey CUSTOMER = new UiStateKey("customer.Id");
    private static final String HINT = "The entry may not be shown.";

    private UiState uiState;
    private FilterHintViewHelper viewHelper;

    @BeforeEach
    void setUp() {
        uiState = new UiState();
        var messages = mock(MessageSourceAccessor.class);
        when(messages.getMessage(anyString(), anyString())).thenReturn(HINT);
        viewHelper = new FilterHintViewHelper(uiState, messages);
    }

    @Test
    void withoutAFilterTheMessageStaysAsItIs() {
        assertThat(viewHelper.appendTo("Saved.", FILTER, CUSTOMER)).isEqualTo("Saved.");
    }

    @Test
    void aSetFilterAddsTheHint() {
        uiState.setValue(CUSTOMER, "42");
        assertThat(viewHelper.appendTo("Saved.", FILTER, CUSTOMER)).isEqualTo("Saved. " + HINT);
    }

    /** An empty filter is a cleared one — it hides nothing and is no reason for a hint. */
    @Test
    void anEmptyFilterIsNoFilter() {
        uiState.setValue(FILTER, "  ");
        assertThat(viewHelper.appendTo("Saved.", FILTER, CUSTOMER)).isEqualTo("Saved.");
    }

    /** A filter of another list says nothing about this one. */
    @Test
    void onlyTheNamedFiltersCount() {
        uiState.setValue(new UiStateKey("other.Filter"), "abc");
        assertThat(viewHelper.appendTo("Saved.", FILTER, CUSTOMER)).isEqualTo("Saved.");
    }
}
