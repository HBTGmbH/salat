package org.tb.persistence;

import org.hibernate.Session;
import org.tb.bdom.Worklog;

/**
* DAO-class for WorklogAction
* 
* @author sql
*
 */
public class WorklogDAO extends AbstractDAO {
    
    /**
     * Gets the WorklogAction for the given id.
     * 
     * @param long id
     * 
     * @return WorklogAction
     */
    public Worklog getWorklogActionById(long id) {
        return (Worklog)getSession().get(Worklog.class, id);
    }
    
    /**
     * Gets a worklog by timereport id.
     * 
     * @param long timereport id
     * 
     * @return Worklog
     */
    public Worklog getWorklogByTimereportID(long timereportID) {
        return (Worklog)getSession().createQuery("from Worklog w where w.timereport.id = ?").setLong(0, timereportID).uniqueResult();
    }
    
    /**
     * Gets a worklog by jiraWorklogID.
     * 
     * @param long jiraWorklogID
     * 
     * @return Worklog
     */
    public Worklog getWorklogByJiraWorklogID(long jiraWorklogID) {
        return (Worklog)getSession().createQuery("from Worklog w where w.jiraWorklogID = ?").setLong(0, jiraWorklogID).uniqueResult();
    }
    
    public void save(Worklog worklog) {
        Session session = getSession();
        session.saveOrUpdate(worklog);
        session.flush();
    }
    
    public void deleteWorklog(Worklog worklog) {
        getSession().delete(worklog);
        getSession().flush();
    }
    
}
