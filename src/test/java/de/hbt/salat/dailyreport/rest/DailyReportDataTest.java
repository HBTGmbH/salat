package de.hbt.salat.dailyreport.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_HOURS_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_MINUTES_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_KIND_INVALID;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.order.domain.SurchargeKind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// the fields from before #1398 are tested on purpose: they stay readable and writable until they go
@SuppressWarnings("deprecation")
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
        .duration(Duration.ofHours(1).plusMinutes(30))
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
    assertThat(DailyReportData.builder().duration(Duration.ofHours(1).plusMinutes(45)).build().toDuration())
        .isEqualTo(Duration.ofHours(1).plusMinutes(45));
  }

  @Test
  void aMinutesPartOfSixtyOrMoreAddsUp() {
    assertThat(DailyReportData.builder().duration(Duration.ofMinutes(90)).build().toDuration())
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

  /* The time surcharge (#1469): the kind goes both ways, rate and credit are read only - they follow from the
     suborder and the duration, no client sets them. */
  @Test
  void writesKindRateAndCreditButReadsTheKindOnly() {
    var booking = TimereportDTO.builder().duration(Duration.ofHours(4))
        .surchargeKind(SurchargeKind.NIGHT).surchargePercent(25).surchargeCredit(Duration.ofHours(1)).build();
    ObjectMapper mapper = new ObjectMapper();

    var json = mapper.writeValueAsString(DailyReportData.valueOf(booking));
    var read = mapper.readValue(json, DailyReportData.class);

    assertThat(json).contains("\"surchargeKind\":\"NIGHT\"", "\"surchargePercent\":25", "\"surchargeCredit\":\"01:00\"");
    assertThat(read.givenSurchargeKind()).isEqualTo(SurchargeKind.NIGHT);
    assertThat(read.getSurchargePercent()).isNull();
    assertThat(read.getSurchargeCredit()).isNull();
  }

  @Test
  void rejectsASurchargeKindThatNamesNoKind() {
    var data = DailyReportData.builder().surchargeKind("WEEKEND").build();

    assertThatThrownBy(data::givenSurchargeKind)
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_SURCHARGE_KIND_INVALID));
  }
  /* Read, a booking carries its duration as HH:mm and, for older clients, as hours and minutes (#1398). */
  @Test
  void readsTheDurationInTheNewAndTheFormerFields() {
    var booking = TimereportDTO.builder().duration(Duration.ofHours(25).plusMinutes(30)).ticketReferences(List.of()).build();

    var json = new ObjectMapper().writeValueAsString(DailyReportData.valueOf(booking));

    assertThat(json).contains("\"duration\":\"25:30\"", "\"hours\":25", "\"minutes\":30");
  }

  @Test
  void writesTheDurationWithTheNewField() {
    var booking = new ObjectMapper().readValue("{\"employeeorderId\":1,\"duration\":\"04:30\"}", DailyReportData.class);

    assertThat(booking.toDuration()).isEqualTo(Duration.ofHours(4).plusMinutes(30));
    assertThat(booking.getHours()).isEqualTo(4);
    assertThat(booking.getMinutes()).isEqualTo(30);
  }

  @Test
  void writesTheDurationWithTheFormerFields() {
    var booking = new ObjectMapper().readValue("{\"employeeorderId\":1,\"hours\":4,\"minutes\":30}", DailyReportData.class);

    assertThat(booking.toDuration()).isEqualTo(Duration.ofHours(4).plusMinutes(30));
    assertThat(booking.getDuration()).isEqualTo(Duration.ofHours(4).plusMinutes(30));
  }

  @Test
  void theNewFieldWinsOverTheFormerOnes() {
    var booking = new ObjectMapper().readValue("{\"employeeorderId\":1,\"duration\":\"01:15\",\"hours\":4,\"minutes\":30}",
        DailyReportData.class);

    assertThat(booking.toDuration()).isEqualTo(Duration.ofHours(1).plusMinutes(15));
    assertThat(booking.getHours()).isEqualTo(1);
    assertThat(booking.getMinutes()).isEqualTo(15);
  }

  /* Import and REST API compare an incoming booking with a stored one; which fields it came in must not matter. */
  @Test
  void aBookingEqualsTheSameBookingGivenWithTheFormerFields() {
    var mapper = new ObjectMapper();
    var given = mapper.readValue("{\"employeeorderId\":1,\"duration\":\"01:30\"}", DailyReportData.class);
    var former = mapper.readValue("{\"employeeorderId\":1,\"hours\":0,\"minutes\":90}", DailyReportData.class);

    assertThat(given).isEqualTo(former).hasSameHashCodeAs(former);
  }

  @ParameterizedTest
  @ValueSource(strings = {"01:60", "-01:00", "1", "1:5", "PT1H", "01:30 h"})
  void rejectsADurationThatIsNotHoursAndMinutes(String duration) {
    var json = "{\"employeeorderId\":1,\"duration\":\"" + duration + "\"}";

    assertThatThrownBy(() -> new ObjectMapper().readValue(json, DailyReportData.class))
        .isInstanceOf(JacksonException.class);
  }
}
