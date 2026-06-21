package org.tb.common.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UiStateKey {

    private final List<UiStateKeyContributor> contributors;

    public Map<String, String> getParamToKey() {
        return contributors.stream()
            .flatMap(c -> c.getParamToKeyMappings().entrySet().stream())
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
