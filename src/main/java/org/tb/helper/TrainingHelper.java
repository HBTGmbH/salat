package org.tb.helper;

import org.tb.bdom.Employeecontract;

public class TrainingHelper {

    public static int[] getHoursMin(Object[] time) {
        int[] intTime = {0, 0};
        if (time != null && time.length == 2) {
            for (int i = 0; i <= 1; i++) {
                if (time[i] != null && time[i] instanceof Long) {
                    Long t = (Long) time[i];
                    intTime[i] = t.intValue();
                }
            }
        }
        return intTime;
    }

    public static int getMinutesForHourDouble(Double doubleValue) {
        int hours = doubleValue.intValue();
        doubleValue = doubleValue - hours;
        int minutes = doubleValue.intValue() * 60;
        minutes += hours * 60;
        return minutes;
    }

    public static String fromDBtimeToString(Employeecontract ec, int hours, int minutes) {

        int trainingDays = 0;
        int trainingHours = 0;
        int trainingMinutes = 0;
        int restMinutes;

        int totalTrainingMinutes = hours * 60 + minutes;

        int dailyWorkingTimeMinutes = getMinutesForHourDouble(ec.getDailyWorkingTime());

        if (dailyWorkingTimeMinutes != 0) {
            trainingDays = totalTrainingMinutes / dailyWorkingTimeMinutes;
            restMinutes = totalTrainingMinutes % dailyWorkingTimeMinutes;
            trainingHours = restMinutes / 60;
            trainingMinutes = restMinutes % 60;
        }

        StringBuilder trainingString = new StringBuilder();
        if (trainingDays < 10) {
            trainingString.append(0);
        }
        trainingString.append(trainingDays);
        trainingString.append(':');
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

    public static String hoursMinToString(int[] time) {

        int trainingHours = time[0];
        int trainingMinutes = time[1];

        trainingHours += trainingMinutes / 60;
        trainingMinutes = trainingMinutes % 60;

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
