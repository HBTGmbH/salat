package org.tb.persistence;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Monthlyreport;
import org.tb.bdom.Overtime;
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
	private OvertimeDAO overtimeDAO;
	
	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}
	
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
	
//	/**
//	 * Gets the EmployeeContract with the given employee id.
//	 * 
//	 * @param long employeeId
//	 * 
//	 * @return Employeecontract
//	 */
//	public Employeecontract getEmployeeContractByEmployeeId(long employeeId) {
//		return (Employeecontract) getSession().createQuery
//				("from Employeecontract e where e.employee.id = ?").setLong(0, employeeId).uniqueResult();
//	}
	
	/**
	 * Gets the EmployeeContract with the given employee id, that is valid for the given date.
	 * 
	 * @param long employeeId
	 * 
	 * @return Employeecontract
	 */
	public Employeecontract getEmployeeContractByEmployeeIdAndDate(long employeeId, Date date) {
		return (Employeecontract) getSession().createQuery
				("from Employeecontract e where e.employee.id = ? and validfrom <= ? and (validuntil >= ? or validuntil = null)").setLong(0, employeeId).setDate(1, date).setDate(2, date).uniqueResult();
	}
	
	
//	/**
//	 * Gets the EmployeeContract with the given employee contract id, that is valid for the given date.
//	 * 
//	 * @param employeeContractId
//	 * @param date 
//	 * @return Employeecontract
//	 */
//	public Employeecontract getEmployeeContractByIdAndDate(long employeeContractId, Date date) {
//		return (Employeecontract) getSession().createQuery
//				("from Employeecontract e where id = ? and validfrom <= ? and validuntil >= ? ").setLong(0, employeeContractId).setDate(1, date).setDate(2, date).uniqueResult();
//	}
	
	
//	/**
//	 * Gets the EmployeeContracts with the given employee id, that are valid for the given dates.
//	 * 
//	 * @param long employeeId
//	 * @param date1
//	 * @param date2
//	 * 
//	 * @return Employeecontract
//	 */
//	public List<Employeecontract> getEmployeeContractsByEmployeeIdAndDates(long employeeId, Date date1, Date date2) {
//		return (List<Employeecontract>) getSession().createQuery
//				("from Employeecontract e where e.employee.id = ? and ((validfrom <= ? and validuntil >= ? ) or (e.employee.id = ? and validfrom <= ? and validuntil >= ? )) order by validfrom").setLong(0, employeeId).setDate(1, date1).setDate(2, date1).setDate(3, date2).setDate(4, date2).list();
//	}
	
//	/**
//	 * Gets the EmployeeContract with the given employee name.
//	 * 
//	 * @param String first
//	 * @param String last
//	 * 
//	 * @return Employeecontract
//	 */
//	public Employeecontract getEmployeeContractByEmployeeName(String first, String last) {
//		Employee emp = (Employee) getSession().createQuery
//			("from Employee e where e.firstname = ? and e.lastname = ?").setString(0, first).setString(1, last).uniqueResult();
//		if (emp == null) return null;
//		
//		Employeecontract ec = (Employeecontract) getSession().createQuery
//			("from Employeecontract e where e.employee.id = ?").setLong(0, emp.getId()).uniqueResult();
//		
//		return ec;
//	}
	
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
	 * Calls {@link EmployeecontractDAO#save(Employeecontract, Employee)} with {@link Employee} = null.
	 * @param ec
	 */
	public void save(Employeecontract ec) {
		save(ec, null);
	}
	
	/**
	 * Saves the given Employeecontract and sets creation-/update-user and creation-/update-date.
	 * 
	 * @param Employeecontract ec
	 */
	public void save(Employeecontract ec, Employee loginEmployee) {
		if (loginEmployee == null) {
			throw new RuntimeException("the login-user must be passed to the db");
		}
		Session session = getSession();
		java.util.Date creationDate = ec.getCreated();
		if (creationDate == null) {
			ec.setCreated(new java.util.Date());
			ec.setCreatedby(loginEmployee.getSign());
		} else {
			ec.setLastupdate(new java.util.Date());
			ec.setLastupdatedby(loginEmployee.getSign());
			Integer updateCounter = ec.getUpdatecounter();
			updateCounter = (updateCounter == null) ? 1 : updateCounter +1;
			ec.setUpdatecounter(updateCounter);
		}
		session.saveOrUpdate(ec);
		session.flush();
//		session.clear();
	}

	/**
	 * Get a list of all Employeecontracts ordered by lastname.
	 * 
	 * @return List<Employeecontract>
	 */
	public List<Employeecontract> getEmployeeContracts() {
		return getSession().createQuery("from Employeecontract e order by employee.lastname asc, validFrom asc").list();
	}
	
	/**
	 * Get a list of all Employeecontracts where the hide flag is unset or that is currently valid ordered by employee sign.
	 * 
	 * @return List<Employeecontract>
	 */
	public List<Employeecontract> getVisibleEmployeeContractsOrderedByEmployeeSign() {
		java.util.Date date = new Date();
		Boolean hide = false;
		return getSession().createQuery("from Employeecontract e where hide = ? or (validFrom <= ? and validUntil >= ?) order by employee.sign asc, validFrom asc").setBoolean(0, hide).setDate(1, date).setDate(2, date).list();
	}

//	/**
//	 * 
//	 * @param date
//	 * @return Returns a list of all {@link Employeecontract}s that are valid for the given date.
//	 */
//	public List<Employeecontract> getEmployeeContractsValidForDate(java.util.Date date) {
//		return getSession().createQuery("from Employeecontract e where e.validFrom <= ? and e.validUntil >= ? order by employee.lastname").setDate(0, date).setDate(1, date).list();
//	}
	
	
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
					List<Overtime> overtimes = overtimeDAO.getOvertimesByEmployeeContractId(ecToDelete.getId());
					for (Overtime overtime : overtimes) {
						overtimeDAO.deleteOvertimeById(overtime.getId());
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
