package org.tb.dailyreport.viewhelper;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import org.tb.dailyreport.domain.TrainingInformation;

public class TrainingHelper {

    public static int[] getHoursMin(TrainingInformation trainingInformation) {
        if(trainingInformation == null) return new int[] {0, 0};
        return new int[] {(int) trainingInformation.getDurationHours(), (int) trainingInformation.getDurationMinutes()};
    }

    public static String hoursMinToString(int[] time) {

        long trainingHours = time[0];
        long trainingMinutes = time[1];

        trainingHours += trainingMinutes / MINUTES_PER_HOUR;
        trainingMinutes = trainingMinutes % MINUTES_PER_HOUR;

        StringBuilder trainingString = new StringBuilder();

        if (trainingHours < 10) {
            trainingString.append(0);
        }
        trainingString.append(trainingHours);
        trainingString.append(':');
        if (trainingMinutes < 10) {
            trainingString.append(0);
        }
        trainingString.append(trainingMinutes);

        return trainingString.toString();
    }

}
