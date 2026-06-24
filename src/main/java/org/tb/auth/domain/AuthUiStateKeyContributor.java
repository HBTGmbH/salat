package org.tb.auth.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.tb.common.web.SensitiveUiStateKey;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

@Component
public class AuthUiStateKeyContributor implements UiStateKeyContributor {

    public static final SensitiveUiStateKey IMPERSONATE_LOGIN_SIGN =
        new SensitiveUiStateKey("impersonateLoginSign");

    @Override
    public Map<String, UiStateKey> getParamToKeyMappings() {
        return Map.of();
    }

    @Override
    public Collection<UiStateKey> getAllKeys() {
        return Set.of(IMPERSONATE_LOGIN_SIGN);
    }
}
