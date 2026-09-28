package de.hbt.salat.dailyreport.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import tools.jackson.databind.ObjectMapper;

class DailyReportDataTest {

  @Test
  void testJacksonSerializationDeserialization() {
    // given
    var timereport = DailyReportData.builder()
        .id(10L)
        .employeeorderId(1L)
        .date(LocalDate.of(2026, 6, 25).toString())
        .orderLabel("test")
        .suborderLabel("test")
        .comment("test")
        .hours(1)
        .minutes(30)
        .orderSign("17")
        .suborderSign("17/01")
        .training(true)
        .ticketReference("ERP-1")
        .build();
    ObjectMapper mapper = new ObjectMapper();

    // when
    var jsonString = mapper.writeValueAsString(timereport);
    var readTinmereport = mapper.readValue(jsonString, DailyReportData.class);

    // then
    assert timereport.equals(readTinmereport);
  }

  /* A client that does not know the field sends none; that is not the same as an empty text (#1140). */
  @Test
  void tellsAMissingTicketReferenceFromAnEmptyOne() {
    ObjectMapper mapper = new ObjectMapper();

    var required = "\"employeeorderId\":1,\"hours\":1,\"minutes\":0";

    var missing = mapper.readValue("{" + required + "}", DailyReportData.class);
    var empty = mapper.readValue("{" + required + ",\"ticketReference\":\"\"}", DailyReportData.class);

    assertThat(missing.getTicketReference()).isNull();
    assertThat(empty.getTicketReference()).isEmpty();
  }

  /* The training flag is optional: a client that leaves it out or sends null books an ordinary
     booking instead of being rejected (#1140). */
  @Test
  void readsAMissingOrNullTrainingFlagAsFalse() {
    ObjectMapper mapper = new ObjectMapper();
    var required = "\"employeeorderId\":1,\"hours\":1,\"minutes\":0";

    var missing = mapper.readValue("{" + required + "}", DailyReportData.class);
    var explicitNull = mapper.readValue("{" + required + ",\"training\":null}", DailyReportData.class);
    var set = mapper.readValue("{" + required + ",\"training\":true}", DailyReportData.class);

    assertThat(missing.isTraining()).isFalse();
    assertThat(explicitNull.isTraining()).isFalse();
    assertThat(set.isTraining()).isTrue();
  }

  @Test
  void takesTheTicketReferenceFromTheBooking() {
    var booking = TimereportDTO.builder().duration(Duration.ofMinutes(90)).ticketReference("ERP-1").build();

    assertThat(DailyReportData.valueOf(booking).getTicketReference()).isEqualTo("ERP-1");
  }

}