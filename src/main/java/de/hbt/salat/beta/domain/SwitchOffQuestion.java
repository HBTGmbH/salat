package de.hbt.salat.beta.domain;

/** A beta the person switched off and has not yet been asked about (#1447), for the settings page. */
public record SwitchOffQuestion(String featureKey, String labelKey) {
}
