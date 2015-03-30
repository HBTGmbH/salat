package org.tb.tasks;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang.time.DateUtils;
import org.springframework.scheduling.timer.ScheduledTimerTask;
import org.tb.GlobalConstants;

/**
 * 
 * @author mgo
 *
 */
public class ExecuteFailedWorklogsScheduledTimerTask extends ScheduledTimerTask {

	
	public ExecuteFailedWorklogsScheduledTimerTask() {
		super();

		int execute_at = GlobalConstants.EXECUTE_FAILED_JIRA_WORKLOGS_AT;
		int execute_every = GlobalConstants.EXECUTE_FAILED_JIRA_WORKLOGS_EVERY;
		
		Calendar startCalendar = DateUtils.truncate(Calendar.getInstance(Locale.GERMAN), Calendar.DAY_OF_MONTH);
        Date startDate = DateUtils.addMinutes(startCalendar.getTime(), execute_at);
        Date now = new Date();
		
		/*
         * Falls der Startzeitpunkt in der Vergangenheit liegt, solange den Interval hinzufügen, bis der Startzeitpunkt
         * in der Zukunft liegt.
         */
        if (startDate.before(now)) {
            do {
                startDate = DateUtils.addMinutes(startDate, execute_every);
            } while(!startDate.after(now));
        /*
         * Falls der Startzeitpunkt in der Zukunft liegt, wird entsprechend obigem Verfahren der Startzeitpunkt so
         * nah wie möglich zurückgesetzt, bis er möglichst kurz in der Zukunft liegt.
         */
        } else {
            do {
                startDate = DateUtils.addMinutes(startDate, -execute_every);
            } while(!startDate.before(now));
            startDate = DateUtils.addMinutes(startDate, execute_every);
        }
        
        long execute_every_milliseconds = execute_every * 60 * 1000;
        long execute_in = startDate.getTime() - now.getTime();
        
        setDelay(execute_in);
        setPeriod(execute_every_milliseconds);
	}
}
