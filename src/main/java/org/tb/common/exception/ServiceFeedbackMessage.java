package org.tb.common.exception;

import static org.tb.common.exception.ServiceFeedbackMessage.Severity.ERROR;
import static org.tb.common.exception.ServiceFeedbackMessage.Severity.INFO;

import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServiceFeedbackMessage {

  public enum Severity { INFO, ERROR }

  private final ErrorCode errorCode;
  private final Severity  severity;
  private final List<Object> arguments;

  public static ServiceFeedbackMessage error(ErrorCode errorCode) {
    return new ServiceFeedbackMessage(errorCode, ERROR, List.of());
  }

  public static ServiceFeedbackMessage error(ErrorCode errorCode, Object... arguments) {
    var args = arguments != null ? Arrays.asList(arguments) : List.of();
    return new ServiceFeedbackMessage(errorCode, ERROR, args);
  }

  public static ServiceFeedbackMessage info(ErrorCode errorCode, Object... arguments) {
    var args = arguments != null ? Arrays.asList(arguments) : List.of();
    return new ServiceFeedbackMessage(errorCode, INFO, args);
  }

  public boolean isError() {
    return severity == ERROR;
  }

}
