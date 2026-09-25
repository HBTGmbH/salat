package org.tb.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tb.common.web.UiStateKey;

/**
 * The switches of the flat-rate list are remembered the way those of the rate list next door are
 * (#1098): only a registered parameter reaches the {@code UiStateFilter}, an unregistered one falls
 * back to its default on every page view. The keys stay apart because the parameter mapping is
 * global — one key for two lists would make them share one remembered value (#952).
 */
class BudgetUiStateKeyContributorTest {

    private final Map<String, UiStateKey> paramToKey =
        new BudgetUiStateKeyContributor().getParamToKeyMappings();

    @Test
    void bothSwitchesOfTheFlatRateListAreRegistered() {
        assertThat(paramToKey)
            .describedAs("an unregistered switch is never remembered")
            .containsKeys("fFlatRateShowInactive", "fFlatRateShowInactiveOrders");
    }

    @Test
    void theFlatRateListDoesNotShareItsKeysWithTheRateList() {
        assertThat(paramToKey.get("fFlatRateShowInactive"))
            .isNotEqualTo(paramToKey.get("fPricingShowInactive"));
        assertThat(paramToKey.get("fFlatRateShowInactiveOrders"))
            .isNotEqualTo(paramToKey.get("fPricingShowInactiveOrders"));
    }

    /**
     * The flat rate's own validity and the validity of its order are two different things, so the
     * two switches of the one list do not share a key either (#957).
     */
    @Test
    void theTwoSwitchesOfTheFlatRateListDoNotShareOneKey() {
        assertThat(paramToKey.get("fFlatRateShowInactive"))
            .isNotEqualTo(paramToKey.get("fFlatRateShowInactiveOrders"));
    }
}
