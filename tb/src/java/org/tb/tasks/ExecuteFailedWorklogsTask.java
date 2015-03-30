package org.tb.tasks;

import java.util.TimerTask;

import org.springframework.transaction.annotation.Transactional;
import org.tb.helper.JiraSalatHelper;
import org.tb.logging.TbLogger;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorklogDAO;
import org.tb.persistence.WorklogMemoryDAO;

/**
 * Task that executes failed Jira requests for creating, deleting or modifying Jira Worklogs
 * 
 * @author mgo
 *
 */

public class ExecuteFailedWorklogsTask extends TimerTask {

	private WorklogMemoryDAO worklogMemoryDAO;
	private TimereportDAO timereportDAO;
	private WorklogDAO worklogDAO;
	
	public void setWorklogMemoryDAO(WorklogMemoryDAO worklogMemoryDAO) {
		this.worklogMemoryDAO = worklogMemoryDAO;
	}
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	public void setWorklogDAO(WorklogDAO worklogDAO) {
		this.worklogDAO = worklogDAO;
	}

	@Transactional
	public void run() {
		TbLogger.info(this.getClass().getName(), "Starting execution of failed Worklogs...");
		try {
			JiraSalatHelper.executeFailedWorklogs(worklogMemoryDAO, timereportDAO, worklogDAO);
		} catch (RuntimeException e) {
			TbLogger.error(this.getClass().getName(), "executeFailedWorklogs has failed:\n" + e.getMessage());
			throw e;
		}
	}
}
