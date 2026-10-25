package de.hbt.salat.graphqlspike.config;

import graphql.ErrorClassification;
import lombok.Getter;

/**
 * An error of the spike itself (#1532), with a code of its own instead of a new {@code ErrorCode}: the spike
 * leaves {@code common} and the message bundles untouched, so that deleting it leaves nothing behind.
 */
@Getter
public class SpikeException extends RuntimeException {

  public static final String IDEMPOTENCY_CONFLICT = "SPIKE-0001";
  public static final String INVALID_INPUT = "SPIKE-0002";
  public static final String NOT_FOUND = "SPIKE-0003";

  /** Not among Spring's {@code ErrorType}s; the concept answers a version conflict and a reused key with it (6.3, 6.5). */
  public static final ErrorClassification CONFLICT = ErrorClassification.errorClassification("CONFLICT");

  private final String code;
  private final ErrorClassification classification;

  public SpikeException(String code, ErrorClassification classification, String message) {
    super(message);
    this.code = code;
    this.classification = classification;
  }
}
