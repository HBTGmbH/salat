package de.hbt.salat.auth.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.web.SensitiveUiStateKey;
import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;

@Component
public class AuthUiStateKeyContributor implements UiStateKeyContributor {

    public static final SensitiveUiStateKey IMPERSONATE_LOGIN_SIGN =
        new SensitiveUiStateKey("impersonateLoginSign");

    public static final SensitiveUiStateKey IMPERSONATE_LOGIN_STATUS =
        new SensitiveUiStateKey("impersonateLoginStatus");

    /** The id of the impersonated login (#1330), next to its sign and status. */
    public static final SensitiveUiStateKey IMPERSONATE_LOGIN_ID =
        new SensitiveUiStateKey("impersonateLoginId");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.of();
    }

    @Override
    public Collection<UiStateKey> getAllKeys() {
        return Set.of(IMPERSONATE_LOGIN_SIGN, IMPERSONATE_LOGIN_STATUS, IMPERSONATE_LOGIN_ID);
    }
}
