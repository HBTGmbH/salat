package org.tb.common.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import org.springframework.stereotype.Component;

import static java.util.function.Function.identity;

/**
 * Aggregates all {@link UiStateKeyContributor} beans into a single param-to-key map at startup.
 */
@Component
public class UiStateKeyRegistry {

    @Getter
    private final Map<String, UiStateKey> paramToKey;
    private final Map<String, UiStateKey> nameToKey;

    public UiStateKeyRegistry(List<UiStateKeyContributor> contributors) {
        this.paramToKey = contributors.stream()
            .flatMap(c -> c.getParamToKeyMappings().entrySet().stream())
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        this.nameToKey = paramToKey.values().stream()
            .collect(Collectors.toUnmodifiableMap(UiStateKey::getName, identity(), (a, b) -> a));
    }

    public Optional<UiStateKey> findByName(String name) {
        return Optional.ofNullable(nameToKey.get(name));
    }
}
