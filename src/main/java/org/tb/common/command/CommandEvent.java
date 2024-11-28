package org.tb.common.command;

public interface CommandEvent<T> {
  T getResult();
  void setResult(T result);
}
