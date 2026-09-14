package org.tb.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.StaticMessageSource;

/**
 * Der Zusatz, der einen ausgeblendeten Eintrag in einer Auswahl als solchen ausweist (#1005). Er
 * hängt an der Beschriftung, wird also mit ihr verkettet — deshalb ist das führende Leerzeichen
 * Teil des Ergebnisses und der Leerstring der Normalfall, nicht {@code null}.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class HiddenMarkerViewHelperTest {

  private HiddenMarkerViewHelper viewHelper;

  @BeforeEach
  public void setUp() {
    var messageSource = new StaticMessageSource();
    messageSource.addMessage("main.general.hidden.suffix", Locale.GERMANY, "verborgen");
    viewHelper = new HiddenMarkerViewHelper(new MessageSourceAccessor(messageSource, Locale.GERMANY));
  }

  @Test
  public void should_mark_a_hidden_record() {
    assertThat("co - Beschreibung" + viewHelper.suffix(true)).isEqualTo("co - Beschreibung (verborgen)");
  }

  @Test
  public void should_leave_a_visible_record_alone() {
    assertThat("co - Beschreibung" + viewHelper.suffix(false)).isEqualTo("co - Beschreibung");
  }

  /** {@code Customer.hide} und {@code Customerorder.hide} sind nullable. */
  @Test
  public void should_treat_an_unset_flag_as_visible() {
    assertThat(viewHelper.suffix(null)).isEmpty();
  }

}
