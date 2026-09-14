package org.tb.common.viewhelper;

import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

/**
 * The marker appended to a select option whose record is hidden (#1005).
 *
 * <p>A select box leaves hidden records out — that is what the {@code hide} flag is for. The one
 * exception is the record a form already stores: it stays in the list, because dropping it would
 * make the form show, and on save write back, something the user never chose. An entry that is only
 * there for that reason has to say so, otherwise the form claims a record is available for picking
 * when it is not.
 *
 * <p>Used from templates as
 * {@code th:text="|${c.shortname} - ${c.name}| + ${@hiddenMarkerViewHelper.suffix(c.hide)}"}. One
 * place for it, because the alternative was the same conditional and the same message lookup in a
 * dozen option tags — the budget forms had written it out four times before this (#956).
 */
@Component
@RequiredArgsConstructor
public class HiddenMarkerViewHelper {

    private final MessageSourceAccessor messages;

    /**
     * {@code " (verborgen)"} for a hidden record, the empty string otherwise. Takes a {@code Boolean}
     * so that both the nullable flags ({@code Customer}, {@code Customerorder}) and the primitive
     * ones ({@code Suborder}) can be passed unchanged.
     */
    public String suffix(Boolean hide) {
        if (!Boolean.TRUE.equals(hide)) {
            return "";
        }
        return " (" + messages.getMessage("main.general.hidden.suffix", "hidden") + ")";
    }

}
