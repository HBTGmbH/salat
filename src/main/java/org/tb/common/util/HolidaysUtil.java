package org.tb.common.util;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool class for easier handling of new holidays
 *
 * @author kd
 */
@Slf4j
public class HolidaysUtil {

    private static final String EASTERN_SUNDAYS_RESOURCE_NAME = "eastern.csv";
    private static final String DATE_FORMAT = "dd.MM.yyyy";

    /**
     * Loads the dates of the eastern sundays from <code>EASTERN_SUNDAYS_RESOURCE_NAME</code>
     */
    public static List<LocalDate> loadEasterSundayDates() {
        InputStream is = HolidaysUtil.class.getClassLoader().getResourceAsStream(EASTERN_SUNDAYS_RESOURCE_NAME);
        if (is == null) {
            log.error("Could no load resource '{}'!", EASTERN_SUNDAYS_RESOURCE_NAME);
            return List.of();
        }

        Scanner scanner = new Scanner(is, "UTF-8");
        List<LocalDate> result = new ArrayList<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                LocalDate eastern = DateUtils.parse(line, DATE_FORMAT);
                result.add(eastern);
            } catch (IllegalArgumentException e) {
                log.error("Could not parse '{}' to date from resource/file '{}'", line, EASTERN_SUNDAYS_RESOURCE_NAME);
            }
        }
        scanner.close();

        return result;
    }

}
