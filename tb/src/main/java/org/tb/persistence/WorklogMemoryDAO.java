package org.tb.persistence;

import java.util.List;

import org.hibernate.Session;
import org.tb.bdom.WorklogMemory;

/**
 * DAO class for "Worklog's" that are not saved in jira (but timereports exist in 'Salat')
 * @author jh
 *
 */
public class WorklogMemoryDAO extends AbstractDAO {
		
	@SuppressWarnings("unchecked")
	public List<WorklogMemory> getAllWorklogMemory() {
		return getSession().createQuery("from WorklogMemory").list();
	}
	
	public void save(WorklogMemory worklogMemory) {
		Session session = getSession();
		
		if (session.contains(worklogMemory)) {
            // existing and attached to session
            session.saveOrUpdate(worklogMemory);
        } else {
            if (worklogMemory.getId() != 0L) {
                // existing but detached from session
                session.merge(worklogMemory);
            } else {
                // new object -> persist it!
                session.saveOrUpdate(worklogMemory);
            }
        }
        
        session.flush();
	}
	
	public boolean delete(WorklogMemory worklogMemory) {
        List<WorklogMemory> worklogMemories = getAllWorklogMemory();
        boolean deleted = false;
        if (!worklogMemories.isEmpty()) {
            getSession().delete(worklogMemory);
            getSession().flush();
            deleted = true;
        }
        return deleted;
	}
}
