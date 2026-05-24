package org.tb.dailyreport.viewhelper;

import java.time.Duration;
import org.tb.dailyreport.domain.TrainingInformation;

public class TrainingHelper {

    public static Duration toDuration(TrainingInformation trainingInformation) {
        if (trainingInformation == null) return Duration.ZERO;
        return Duration.ofHours(trainingInformation.getDurationHours())
            .plusMinutes(trainingInformation.getDurationMinutes());
    }

}
