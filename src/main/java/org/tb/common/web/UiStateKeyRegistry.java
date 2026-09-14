package org.tb.common.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.Getter;
import org.springframework.stereotype.Component;

import static java.util.function.Function.identity;

/**
 * Aggregates all {@link UiStateKeyContributor} beans into a single param-to-key map at startup.
 */
@Component
public class UiStateKeyRegistry {

    /**
     * Every remembered parameter is named {@code f} plus a capital letter (ADR-0022). The filter
     * namespace and the form-field namespace are disjoint that way: a form field is never called
     * {@code f…}, a UiState parameter always is. Without the rule a name a module registers —
     * {@code month}, {@code orderId} — silently becomes a fallback value in every form of the
     * application that happens to use the same name (#999).
     */
    static final Pattern FILTER_PARAM = Pattern.compile("^f[A-Z][A-Za-z0-9]*$");

    @Getter
    private final Map<String, UiStateKey> paramToKey;
    private final Map<String, UiStateKey> nameToKey;

    public UiStateKeyRegistry(List<UiStateKeyContributor> contributors) {
        this.paramToKey = contributors.stream()
            .flatMap(c -> c.getParamToKeyMappings().entrySet().stream())
            .peek(e -> requireFilterParamName(e.getKey()))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        this.nameToKey = contributors.stream()
            .flatMap(c -> c.getAllKeys().stream())
            .collect(Collectors.toUnmodifiableMap(UiStateKey::getName, identity(), (a, b) -> a));
    }

    public Optional<UiStateKey> findByName(String name) {
        return Optional.ofNullable(nameToKey.get(name));
    }

    private static void requireFilterParamName(String paramName) {
        if (!FILTER_PARAM.matcher(paramName).matches()) {
            throw new IllegalStateException(
                "UiState parameter '" + paramName + "' must be named f followed by a capital letter"
                    + " (e.g. fCustomerFilter) — see ADR-0022. A parameter without the prefix is fed"
                    + " to every form field of the same name as a fallback value.");
        }
    }
}
