package de.hbt.salat.dailyreport.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_HOURS_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_MINUTES_INVALID;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.exception.InvalidDataException;
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
  void takesTheTicketReferencesFromTheBooking() {
    var booking = TimereportDTO.builder().duration(Duration.ofMinutes(90)).ticketReferences(List.of("ERP-1", "ERP-2")).build();

    var data = DailyReportData.valueOf(booking);
    assertThat(data.getTicketReferences()).containsExactly("ERP-1", "ERP-2");
    // the first, for clients that know one reference only (#1326)
    assertThat(data.getTicketReference()).isEqualTo("ERP-1");
  }

  @Test
  void aBookingWithoutReferencesHasNoneInEitherField() {
    var booking = TimereportDTO.builder().duration(Duration.ofMinutes(90)).ticketReferences(List.of()).build();

    var data = DailyReportData.valueOf(booking);
    assertThat(data.getTicketReferences()).isEmpty();
    assertThat(data.getTicketReference()).isNull();
  }

  /** The list wins; without it the single reference of an older client is the only one; without both nothing is said. */
  @Test
  void givenReferencesPreferTheListOverTheSingleOne() {
    ObjectMapper mapper = new ObjectMapper();
    var required = "\"employeeorderId\":1,\"date\":\"2026-06-15\",\"hours\":1,\"minutes\":0";
    var both = mapper.readValue("{" + required + ",\"ticketReference\":\"OLD-1\",\"ticketReferences\":[\"A-1\",\"B-2\"]}",
        DailyReportData.class);
    var single = mapper.readValue("{" + required + ",\"ticketReference\":\"OLD-1\"}", DailyReportData.class);
    var none = mapper.readValue("{" + required + "}", DailyReportData.class);

    assertThat(both.givenTicketReferences()).containsExactly("A-1", "B-2");
    assertThat(single.givenTicketReferences()).containsExactly("OLD-1");
    assertThat(none.givenTicketReferences()).isNull();
  }

  /** Hours and minutes apart exist only in this format; inside the application the duration is one value (#1400). */
  @Test
  void hoursAndMinutesBecomeOneDuration() {
    assertThat(DailyReportData.builder().hours(1).minutes(45).build().toDuration())
        .isEqualTo(Duration.ofHours(1).plusMinutes(45));
  }

  @Test
  void aMinutesPartOfSixtyOrMoreAddsUp() {
    assertThat(DailyReportData.builder().hours(0).minutes(90).build().toDuration())
        .isEqualTo(Duration.ofHours(1).plusMinutes(30));
  }

  @Test
  void negativeHoursOrMinutesAreRefusedWithTheirOwnCode() {
    assertThatThrownBy(() -> DailyReportData.builder().hours(-1).minutes(0).build().toDuration())
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_DURATION_HOURS_INVALID.getCode());
    assertThatThrownBy(() -> DailyReportData.builder().hours(1).minutes(-5).build().toDuration())
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_DURATION_MINUTES_INVALID.getCode());
  }
}