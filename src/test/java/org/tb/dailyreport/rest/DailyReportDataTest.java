package org.tb.dailyreport.rest;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DailyReportDataTest {

  @Test
  void testJacksonSerializationDeserialization() {
    // given
    var timereport = DailyReportData.builder()
        .id(10L)
        .employeeorderId(1L)
        .date(LocalDate.now().toString())
        .orderLabel("test")
        .suborderLabel("test")
        .comment("test")
        .hours(1)
        .minutes(30)
        .orderSign("17")
        .suborderSign("17/01")
        .training(true)
        .build();
    ObjectMapper mapper = new ObjectMapper();

    // when
    var jsonString = mapper.writeValueAsString(timereport);
    var readTinmereport = mapper.readValue(jsonString, DailyReportData.class);

    // then
    assert timereport.equals(readTinmereport);
  }

}