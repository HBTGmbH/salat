package de.hbt.salat.common.web;

import lombok.Data;

/**
 * A typed, named key for a slot in {@link UiState}.
 * Module-specific constants are declared in the module's {@link UiStateKeyContributor}.
 */
@Data
public class UiStateKey {

    private final String name;
}
