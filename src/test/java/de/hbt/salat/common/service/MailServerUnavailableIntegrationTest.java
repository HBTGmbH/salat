package de.hbt.salat.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import de.hbt.salat.common.service.MailService.MailContact;

/**
 * Ein Mailserver, der Verbindungen annimmt und nie antwortet (#1582). Der Health-Endpunkt, den die
 * Plattform abfragt, bleibt davon unberührt, und ein Versand bricht nach dem Zeitlimit ab, statt
 * einen Thread festzuhalten. Über einen echten Port, weil erst die ganze Kette sagt, was die Sonde
 * der Plattform zu sehen bekommt.
 *
 * <p>Eigene H2-Datenbank, aus demselben Grund wie in {@code ControllerAuthorizationIntegrationTest}.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1582;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR"
})
@ActiveProfiles({"unittest", "local"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class MailServerUnavailableIntegrationTest {

  /** Nimmt Verbindungen an (Backlog des Betriebssystems) und schickt nie die Begrüßung des SMTP-Servers. */
  private static final ServerSocket SILENT_MAIL_SERVER = openSilentMailServer();

  @LocalServerPort
  private int port;
  @Autowired
  private MailService mailService;

  @DynamicPropertySource
  static void mailServer(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", () -> "localhost");
    registry.add("spring.mail.port", SILENT_MAIL_SERVER::getLocalPort);
  }

  @AfterAll
  static void closeMailServer() throws IOException {
    SILENT_MAIL_SERVER.close();
  }

  @Test
  void the_health_endpoint_stays_up() throws Exception {
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
        .timeout(Duration.ofSeconds(30))
        .GET().build();

    var response = client.send(request, BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"UP\"");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = SEPARATE_THREAD)
  void sending_gives_up_after_the_timeout_and_logs_the_error() {
    var appender = new ListAppender<ILoggingEvent>();
    var logger = (Logger) LoggerFactory.getLogger(MailService.class);
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatThrownBy(() -> mailService.sendEmail("Betreff", "Text",
          new MailContact("Salat", "salat@example.org"), new MailContact("Person A", "person-a@example.org")))
          .isInstanceOf(RuntimeException.class);

      assertThat(appender.list).anySatisfy(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).startsWith("Could not send Email to person-a@example.org");
      });
    } finally {
      logger.detachAppender(appender);
    }
  }

  private static ServerSocket openSilentMailServer() {
    try {
      return new ServerSocket(0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
