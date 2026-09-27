package org.tb.common.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Hält den MDC eines Ereignisses beim Schreiben fest. Logback liest ihn sonst erst, wenn jemand
 * danach fragt — im Test also nach der Anfrage, wenn der Filter ihn schon abgeräumt hat. Ob vorher
 * ein anderer Appender ihn ausgelesen hat, hängt davon ab, welche Logback-Konfiguration ein zuvor
 * gelaufener Spring-Test hinterlassen hat.
 */
class MdcCapturingAppender extends ListAppender<ILoggingEvent> {

  @Override
  protected void append(ILoggingEvent event) {
    event.prepareForDeferredProcessing();
    super.append(event);
  }

}
