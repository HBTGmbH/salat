package de.hbt.salat.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.web.UiStateKey;

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
    void theSwitchOfTheFlatRateListIsRegistered() {
        assertThat(paramToKey)
            .describedAs("an unregistered switch is never remembered")
            .containsKey("fFlatRateShowInactiveOrders");
    }

    /** A flat rate is never inactive, only its order can be (#1438). */
    @Test
    void theFlatRateListHasNoSwitchForInactiveFlatRates() {
        assertThat(paramToKey).doesNotContainKey("fFlatRateShowInactive");
    }

    @Test
    void theFlatRateListDoesNotShareItsKeyWithTheRateList() {
        assertThat(paramToKey.get("fFlatRateShowInactiveOrders"))
            .isNotEqualTo(paramToKey.get("fPricingShowInactiveOrders"));
    }
}
