package org.tb.common.exception;

import static java.util.stream.Collectors.joining;
import static org.tb.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class ErrorCodeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final List<ServiceFeedbackMessage> messages = new ArrayList<>();

  public ErrorCodeException(ErrorCode errorCode, Object... arguments) {
    super(errorCode.toString());
    if(arguments != null && arguments.length > 0) {
      messages.add(error(errorCode, arguments));
    } else {
      messages.add(error(errorCode));
    }
  }

  public ErrorCodeException(List<ServiceFeedbackMessage> messages) {
    super();
    this.messages.addAll(messages);
  }

  public ErrorCodeException(ErrorCode errorCode) {
    super(errorCode.toString());
    messages.add(error(errorCode));
  }

  public ErrorCodeException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.toString(), cause);
    messages.add(error(errorCode));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getName()).append("\n");
    for (ServiceFeedbackMessage message : messages) {
      sb.append(message.getErrorCode().toString());
      if (!message.getArguments().isEmpty()) {
        sb.append(" - ").append(message.getArguments().stream().map(Object::toString).collect(joining(",")));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}
