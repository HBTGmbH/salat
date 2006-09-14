package org.tb.persistence;

import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Monthlyreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Vacation;

/**
 * DAO class for 'Employeecontract'
 * 
 * @author oda
 *
 */
public class EmployeecontractDAO extends HibernateDaoSupport {

	private EmployeeorderDAO employeeorderDAO;
	private MonthlyreportDAO monthlyreportDAO;
	private VacationDAO vacationDAO;
	private TimereportDAO timereportDAO;
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setMonthlyreportDAO(MonthlyreportDAO monthlyreportDAO) {
		this.monthlyreportDAO = monthlyreportDAO;
	}

	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}

	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	/**
	 * Gets the EmployeeContract with the given employee id.
	 * 
	 * @param long employeeId
	 * 
	 * @return Employeecontract
	 */
	public Employeecontract getEmployeeContractByEmployeeId(long employeeId) {
		return (Employeecontract) getSession().createQuery
				("from Employeecontract e where e.employee.id = ?").setLong(0, employeeId).uniqueResult();
	}
	
	/**
	 * Gets the EmployeeContract with the given employee name.
	 * 
	 * @param String first
	 * @param String last
	 * 
	 * @return Employeecontract
	 */
	public Employeecontract getEmployeeContractByEmployeeName(String first, String last) {
		Employee emp = (Employee) getSession().createQuery
			("from Employee e where e.firstname = ? and e.lastname = ?").setString(0, first).setString(1, last).uniqueResult();
		if (emp == null) return null;
		
		Employeecontract ec = (Employeecontract) getSession().createQuery
			("from Employeecontract e where e.employee.id = ?").setLong(0, emp.getId()).uniqueResult();
		
		return ec;
	}
	
	/**
	 * Gets the EmployeeContract with the given id.
	 * 
	 * @param long id
	 * 
	 * @return Employeecontract
	 */
	public Employeecontract getEmployeeContractById(long id) {
		return (Employeecontract) getSession().createQuery("from Employeecontract ec where ec.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Saves the given Employeecontract.
	 * 
	 * @param Employeecontract ec
	 */
	public void save(Employeecontract ec) {
		Session session = getSession();
		session.saveOrUpdate(ec);
		session.flush();
	}

	/**
	 * Get a list of all Employeecontracts ordered by lastname.
	 * 
	 * @return List<Employeecontract>
	 */
	public List<Employeecontract> getEmployeeContracts() {
		return getSession().createQuery("from Employeecontract e order by employee.lastname").list();
	}

	/**
	 * Deletes the given employee contract .
	 * 
	 * @param long ecId
	 * 
	 * @return boolean
	 */
	public boolean deleteEmployeeContractById(long ecId) {
		List<Employeecontract> allEmployeecontracts = getEmployeeContracts();
		Employeecontract ecToDelete = getEmployeeContractById(ecId);
		boolean ecDeleted = false;
		
		for (Iterator iter = allEmployeecontracts.iterator(); iter.hasNext();) {
			Employeecontract ec = (Employeecontract) iter.next();
			if(ec.getId() == ecToDelete.getId()) {
				// check if related employeeorders/timereports exist 
				// if so, no deletion possible				

				boolean deleteOk = true;
				List<Employeeorder> allEmployeeorders = employeeorderDAO.getEmployeeorders();
				for (Iterator iter2 = allEmployeeorders.iterator(); iter2.hasNext();) {
					Employeeorder eo = (Employeeorder) iter2.next();
					if (eo.getEmployeecontract().getId() == ecToDelete.getId()) {
						deleteOk = false;
						break;
					}
				}
				
				if (deleteOk) {
					List<Timereport> allTimereports = timereportDAO.getTimereports();
					for (Iterator iter2 = allTimereports.iterator(); iter2.hasNext();) {
						Timereport tr = (Timereport) iter2.next();
						if ((tr.getEmployeecontract() != null) && 
							(tr.getEmployeecontract().getId() == ecToDelete.getId())) {
							deleteOk = false;
							break;
						}
					}
				}
				
				// if ok for deletion, check for monthlyreport and vacation entries and
				// delete them successively (cannot yet be done via web application)				
				if (deleteOk) {
					List<Monthlyreport> allMonthlyreports = monthlyreportDAO.getMonthlyreports();
					for (Iterator iter3 = allMonthlyreports.iterator(); iter3.hasNext();) {
						Monthlyreport mr = (Monthlyreport) iter3.next();
						if (mr.getEmployeecontract().getId() == ecToDelete.getId()) {
							monthlyreportDAO.deleteMonthlyreportById(mr.getId());
						}
					}
				}				
				if (deleteOk) {
					List<Vacation> allVacations = vacationDAO.getVacations();
					for (Iterator iter4 = allVacations.iterator(); iter4.hasNext();) {
						Vacation va = (Vacation) iter4.next();
						if (va.getEmployeecontract().getId() == ecToDelete.getId()) {
							vacationDAO.deleteVacationById(va.getId());
						}
					}
				}
				
				// finally, go for deletion of employeecontract
				if (deleteOk) {
					Session session = getSession();
					session.delete(ecToDelete);
					session.flush();
					ecDeleted = true;
				}
				break;
			}
		}
		
		return ecDeleted;
	}
	
}
