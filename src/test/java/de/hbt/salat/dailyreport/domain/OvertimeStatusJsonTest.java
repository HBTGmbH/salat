package de.hbt.salat.dailyreport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import de.hbt.salat.dailyreport.domain.OvertimeStatus.OvertimeStatusInfo;
import tools.jackson.databind.ObjectMapper;

/**
 * The overtime as one value HH:mm with its sign (#1398); the fields that split it and the ISO-8601 duration come
 * along unchanged for older clients.
 */
@SuppressWarnings("deprecation")
class OvertimeStatusJsonTest {

  @Test
  void theBalanceIsHoursAndMinutesWithItsSign() {
    var info = new OvertimeStatusInfo();
    info.setBalance(Duration.ofHours(-12).minusMinutes(30));
    info.setDuration(Duration.ofHours(-12).minusMinutes(30));
    info.setHours(-12);
    info.setMinutes(-30);
    info.setNegative(true);
    info.setSurchargeCredit(Duration.ofHours(2).plusMinutes(30));

    var json = new ObjectMapper().writeValueAsString(info);

    assertThat(json).contains("\"balance\":\"-12:30\"", "\"surchargeCredit\":\"02:30\"",
        "\"duration\":\"PT-12H-30M\"", "\"hours\":-12", "\"minutes\":-30", "\"negative\":true");
  }

  @Test
  void aBalanceBeyondADayKeepsCountingHours() {
    var info = new OvertimeStatusInfo();
    info.setBalance(Duration.ofHours(40).plusMinutes(30));
    info.setSurchargeCredit(Duration.ZERO);

    assertThat(new ObjectMapper().writeValueAsString(info)).contains("\"balance\":\"40:30\"", "\"surchargeCredit\":\"00:00\"");
  }
}
