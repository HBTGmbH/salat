package org.tb.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

@SpringBootTest
class MessageSourceConfigurationTest {

  @Autowired
  private MessageSource messageSource;

  @Test
  void shouldLoadEnglishMessages() {
    String message = messageSource.getMessage("main.general.mainmenu.menu.text", null, Locale.ENGLISH);
    assertThat(message).isEqualTo("HBT-SALAT");
  }

  @Test
  void shouldLoadGermanMessages() {
    String message = messageSource.getMessage("main.general.mainmenu.menu.text", null, Locale.GERMAN);
    assertThat(message).isEqualTo("HBT-SALAT");
  }

  @Test
  void shouldLoadDifferentMessageInEnglish() {
    String message = messageSource.getMessage("main.general.logout.text", null, Locale.ENGLISH);
    assertThat(message).isEqualTo("Logout");
  }

  @Test
  void shouldLoadDifferentMessageInGerman() {
    String message = messageSource.getMessage("main.general.logout.text", null, Locale.GERMAN);
    assertThat(message).isEqualTo("Abmelden");
  }

  @Test
  void shouldLoadEmployeesMessageInEnglish() {
    String message = messageSource.getMessage("main.general.mainmenu.employees.text", null, Locale.ENGLISH);
    assertThat(message).isEqualTo("Employees");
  }

  @Test
  void shouldLoadEmployeesMessageInGerman() {
    String message = messageSource.getMessage("main.general.mainmenu.employees.text", null, Locale.GERMAN);
    assertThat(message).isEqualTo("Mitarbeiter");
  }
}
