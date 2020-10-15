package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Worklog;

@Component
public class WorklogDAO extends AbstractDAO {

    @Autowired
    public WorklogDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    /**
     * Gets the WorklogAction for the given id.
     */
    public Worklog getWorklogActionById(long id) {
        return (Worklog) getSession().get(Worklog.class, id);
    }

    /**
     * Gets a worklog by timereport id.
     */
    public Worklog getWorklogByTimereportID(long timereportID) {
        return (Worklog) getSession().createQuery("from Worklog w where w.timereport.id = ?").setLong(0, timereportID).uniqueResult();
    }

    /**
     * Gets a worklog by jiraWorklogID.
     */
    public Worklog getWorklogByJiraWorklogID(long jiraWorklogID) {
        return (Worklog) getSession().createQuery("from Worklog w where w.jiraWorklogID = ?").setLong(0, jiraWorklogID).uniqueResult();
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
