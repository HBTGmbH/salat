package org.tb.common.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UiStateKey {

    private final Map<String, String> paramToKey;

    public UiStateKey(List<UiStateKeyContributor> contributors) {
        this.paramToKey = contributors.stream()
            .flatMap(c -> c.getParamToKeyMappings().entrySet().stream())
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, String> getParamToKey() {
        return paramToKey;
    }
}
