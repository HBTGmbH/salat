package org.tb.persistence;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Timereport;
import org.tb.bdom.comparators.EmployeeOrderComparator;

/**
 * DAO class for 'Employeeorder'
 * 
 * @author oda
 *
 */
public class EmployeeorderDAO extends HibernateDaoSupport {

	private EmployeeOrderComparator employeeOrderComparator = new EmployeeOrderComparator();
	private EmployeecontractDAO employeecontractDAO;
	private TimereportDAO timereportDAO;
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	
	/**
	 * Gets the employeeorder for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Employeeorder
	 */
	public Employeeorder getEmployeeorderById(long id) {
		return (Employeeorder) getSession().createQuery("from Employeeorder eo where eo.id = ?").setLong(0, id).uniqueResult();
	}
	
	public List<Employeeorder> getEmployeeordersForEmployeeordercontentWarning(Employeecontract ec) {
//		return (List<Employeeorder>) getSession().createQuery("from Employeeorder eo where eo.employeeOrderContent.contactTechHbt.id = ? or eo.employeecontract.id = ?").setLong(0, ec.getEmployee().getId()).setLong(1, ec.getId()).list();
		return (List<Employeeorder>) getSession().createQuery("from Employeeorder eo where (eo.employeeOrderContent.committed_emp != true and eo.employeecontract.id = ?) or (eo.employeeOrderContent.committed_mgmt != true and eo.employeeOrderContent.contactTechHbt.id = ?)").setLong(0, ec.getId()).setLong(1, ec.getEmployee().getId()).list();
	}
//	/**
//	 * 
//	 * @param employeecontractId
//	 * @param suborderId
//	 * @return
//	 */
//	public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderId(long employeecontractId, long suborderId) {
//		return (Employeeorder) getSession().createQuery("from Employeeorder eo where eo.employeecontract.id = ? and eo.suborder.id = ?").setLong(0, employeecontractId).setLong(1, suborderId).uniqueResult();
//	}
	
	/**
	 * 
	 * @param employeecontractId
	 * @param customerOrderSign
	 * @param date
	 * @return
	 */
	public List<Employeeorder> getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(long employeecontractId, String customerOrderSign, java.sql.Date date) {
		return (List<Employeeorder>) getSession().createQuery("from Employeeorder eo " +
				"where eo.employeecontract.id = ? " +
				"and eo.suborder.customerorder.sign = ? " +
				"and fromdate <= ? " +
				"and (untildate is null " +
					"or untildate >= ? )")
				.setLong(0, employeecontractId)
				.setString(1, customerOrderSign)
				.setDate(2, date)
				.setDate(3, date)
				.list();

	}
	
	
	/**
	 * Returns the {@link Employeeorder} associated to the given employeecontractID and suborderId, that is valid for the given date.
	 * 
	 * @param employeecontractId
	 * @param suborderId
	 * @return
	 */
	public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(long employeecontractId, long suborderId, Date date) {
		return (Employeeorder) getSession().createQuery("from Employeeorder eo where eo.employeecontract.id = ? and eo.suborder.id = ? and fromdate <= ? and untildate >= ?").setLong(0, employeecontractId).setLong(1, suborderId).setDate(2, date).setDate(3, date).uniqueResult();
	}
	
//	/**
//	 * Gets the employeeorder for the given sign.
//	 * 
//	 * @param String sign
//	 * 
//	 * @return Employeeorder
//	 */
//	public Employeeorder getEmployeeorderBySign(String sign) {
//		Employeeorder co = (Employeeorder) getSession().createQuery("from Employeeorder c where c.sign = ?").setString(0, sign).uniqueResult();
//		return co;
//	}
//	
	
	/**
	 * Gets the list of employeeorders for the given employee contract id.
	 * 
	 * @param employeeContractId
	 * @return
	 */
	public List<Employeeorder> getEmployeeOrdersByEmployeeContractId(long employeeContractId) {
		return getSession().createQuery("from Employeeorder where EMPLOYEECONTRACT_ID = ? order by suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").setLong(0, employeeContractId).list();
	}
	
	/**
	 * Gets the list of employeeorders for the given suborder id.
	 * 
	 * @param suborderId
	 * @return
	 */
	public List<Employeeorder> getEmployeeOrdersBySuborderId(long suborderId) {
		return getSession().createQuery("from Employeeorder e where e.suborder.id = ? order by suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").setLong(0, suborderId).list();
	}
	
	
	/**
	 * Gets the list of employeeorders for the given employee contract and suborder id.
	 * 
	 * @param employeeContractId
	 * @param suborderId
	 * @return
	 */
	public List<Employeeorder> getEmployeeOrdersByEmployeeContractIdAndSuborderId(long employeeContractId, long suborderId) {
		return getSession().createQuery("from Employeeorder where EMPLOYEECONTRACT_ID = ? and SUBORDER_ID = ? order by suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").setLong(0, employeeContractId).setLong(1, suborderId).list();
	}
	
	/**
	 * Gets the list of employeeorders for the given employee contract and suborder id and date.
	 * 
	 * @param employeeContractId
	 * @param suborderId
	 * @param date
	 * @return
	 */
	public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(long employeeContractId, long suborderId, Date date) {
		return (List<Employeeorder>) getSession().createQuery("from Employeeorder where EMPLOYEECONTRACT_ID = ? and " +
				"SUBORDER_ID = ? and  " +
				"fromdate <= ? and (untildate >= ? or untildate = null or untildate = '') " +
				"order by suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
				.setLong(0, employeeContractId).setLong(1, suborderId).setDate(2, date).setDate(3, date).list();
	}
	
	/**
	 * Gets the list of employeeorders for the given employee contract and suborder id and date.
	 * 
	 * @param employeeContractId
	 * @param suborderId
	 * @param date
	 * @return
	 */
	public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate3(long employeeContractId, long suborderId, Date date) {
		return (List<Employeeorder>) getSession().createQuery("from Employeeorder where EMPLOYEECONTRACT_ID = ? and " +
				"SUBORDER_ID = ? and  " +
				" (untildate >= ? or untildate = null or untildate = '') " +
				"order by suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
				.setLong(0, employeeContractId).setLong(1, suborderId).setDate(2, date).list();
	}
	
//	/**
//	 * Gets the list of employeeorders for the given employee id.
//	 * 
//	 * @param employeeId
//	 * @return
//	 */
//	public List<Employeeorder> getEmployeeOrdersByEmployeeId(long employeeId) {
//		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeId(employeeId);
//		long employeeContractId = employeecontract.getId();
//		return getEmployeeOrdersByEmployeeContractId(employeeContractId);
//	}
	
	
	
	/**
	 * Get a list of all Employeeorders ordered by their sign.
	 * 
	 * @return List<Employeeorder> 
	 */
	public List<Employeeorder> getEmployeeorders() {
		return getSession().createQuery("from Employeeorder order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").list();
	}

	/**
	 * 
	 * @param orderId
	 * @return Returns a list of all {@link Employeeorder}s associated to the given orderId.
	 */
	public List<Employeeorder> getEmployeeordersByOrderId(long orderId) {
		return getSession().createQuery("from Employeeorder where suborder.customerorder.id = ? order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").setLong(0, orderId).list();
	}
	
	/**
	 * 
	 * @param eocId The id of the associated {@link EmployeeOrderContent}
	 * @return Returns the {@link Employeeorder} associated to the given eocId.
	 */
	public Employeeorder getEmployeeOrderByContentId(long eocId) {
		return (Employeeorder) getSession().createQuery("from Employeeorder eo where eo.employeeOrderContent.id = ? ").setLong(0, eocId).uniqueResult();
	}
	
//	/**
//	 * 
//	 * @param orderId
//	 * @param employeeId
//	 * @return Returns a list of all {@link Employeeorder}s associated to the given orderId and employeeId.
//	 */
//	public List<Employeeorder> getEmployeeordersByOrderIdAndEmployeeId(long orderId, long employeeId) {
//		return getSession().createQuery("from Employeeorder where suborder.customerorder.id = ? and employeecontract.employee.id = ? order by employeecontract.employee.firstname asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").setLong(0, orderId).setLong(1, employeeId).list();
//	}
	
	/**
	 * 
	 * @param orderId
	 * @param employeeContractId
	 * @return Returns a list of all {@link Employeeorder}s associated to the given orderId and employeeContractId.
	 */
	public List<Employeeorder> getEmployeeordersByOrderIdAndEmployeeContractId(long orderId, long employeeContractId) {
		return getSession().createQuery("from Employeeorder where suborder.customerorder.id = ? and employeecontract.id = ? order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc").setLong(0, orderId).setLong(1, employeeContractId).list();
	}
	
	/**
	 * Get a list of all Employeeorders ordered by employee, customer order, suborder.
	 * 
	 * @return List<Employeeorder> 
	 */
	public List<Employeeorder> getSortedEmployeeorders() {
		List<Employeeorder> employeeorders = getSession().createQuery("from Employeeorder").list();
		Collections.sort(employeeorders, employeeOrderComparator);
		return employeeorders;
	}
	
	
	/**
	 * Get a list of all Employeeorders fitting to the given filters ordered by employee, customer order, and suborder.
	 * 
	 * @return List<Employeeorder> 
	 */
	public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, Long employeeContractId, Long customerOrderId, Long customerSuborderId) {
		List<Employeeorder> employeeorders = null;
		if (showInvalid == null || showInvalid == false) {
			Date now = new Date();
			if (filter == null || filter.trim().equals("")) {
				if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 01: only valid, no filter, no employeeContractId, no customerOrderId
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setDate(0, now)
								.setDate(1, now)
								.list();
					} else {
						// case 02: only valid, no filter, no employeeContractId, customerOrderId
					if(customerSuborderId == 0 || customerSuborderId == -1){
						// suborder wasn't selected
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setDate(1, now)
								.setDate(2, now)
								.list();
					}
					else { // suborder was selected
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and suborder.id = ? " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, customerSuborderId)
								.setDate(2, now)
								.setDate(3, now)
								.list();
					}
					}
				} else {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 03: only valid, no filter, employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"employeecontract.id = ? " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, employeeContractId)
								.setDate(1, now)
								.setDate(2, now)
								.list();
					} else {
						// case 04: only valid, no filter, employeeContractId, customerOrderId
						if (customerSuborderId == -1 || customerSuborderId == 0){
							// when no suborder was selected
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.setDate(2, now)
								.setDate(3, now)
								.list();
						}
						else {
							// when suborder was selected
							employeeorders = getSession().createQuery("from Employeeorder eo where " +
									"suborder.customerorder.id = ? " +
									"and employeecontract.id = ? " +
									"and suborder.id = ? " +
									"and fromdate <= ? " +
									"and (untildate = null " +
										"or untildate >= ?) " +
									"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
									.setLong(0, customerOrderId)
									.setLong(1, employeeContractId)
									.setLong(2, customerSuborderId)
									.setDate(3, now)
									.setDate(4, now)
									.list();
						}
					}
				}
			} else {
				if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 05: only valid, filter, no employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"(upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setString(0, filter)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setDate(10, now)
								.setDate(11, now)
								.list();
					} else {
						// case 06: only valid, filter, no employeeContractId, customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.setDate(11, now)
								.setDate(12, now)
								.list();
					}
				} else {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 07: only valid, filter, employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"employeecontract.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, employeeContractId)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.setDate(11, now)
								.setDate(12, now)
								.list();
					} else {
						// case 08: only valid, filter, employeeContractId, customerOrderId  
						
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.setString(11, filter)
								.setDate(12, now)
								.setDate(13, now)
								.list();
					}
				}
			}
		} else {
			if (filter == null || filter.trim().equals("")) {
				if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 09: valid + invalid, no filter, no employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.list();
					} else {
						// case 10: valid + invalid, no filter, no employeeContractId, customerOrderId  
						if(customerSuborderId == 0 || customerSuborderId == -1){
							// suborder was not selected
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.list();
						}
						else{// suborder was selected
							employeeorders = getSession().createQuery("from Employeeorder eo where " +
									"suborder.customerorder.id = ? " +
									"and suborder.id = ? " +
									"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
									.setLong(0, customerOrderId)
									.setLong(1, customerSuborderId)
									.list();
						}
					}
				} else {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 11: valid + invalid, no filter, employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"employeecontract.id = ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, employeeContractId)
								.list();
					} else {
						// case 12: valid + invalid, no filter, employeeContractId, customerOrderId
					if(customerSuborderId == 0 || customerSuborderId == -1){
						// no suborder was selected
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.list();
					}
					// the suborder was selected also
					else {
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"and suborder.id = ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.setLong(2, customerSuborderId)
								.list();
					}
					}
				}
			} else {
				if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 13: valid + invalid, filter, no employeeContractId, no customerOrderId   
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"upper(id) like ? " +
								"or upper(employeecontract.employee.sign) like ? " +
								"or upper(employeecontract.employee.firstname) like ? " +
								"or upper(employeecontract.employee.lastname) like ? " +
								"or upper(suborder.customerorder.sign) like ? " +
								"or upper(suborder.customerorder.description) like ? " +
								"or upper(suborder.customerorder.shortdescription) like ? " +
								"or upper(suborder.sign) like ? " +
								"or upper(suborder.description) like ? " +
								"or upper(suborder.shortdescription) like ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setString(0, filter)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.list();
					} else {
						// case 14: valid + invalid, filter, no employeeContractId, customerOrderId   
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.list();
					}
				} else {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 15: valid + invalid, filter, employeeContractId, no customerOrderId   
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"employeecontract.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, employeeContractId)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.list();
					} else {
						// case 16: valid + invalid, filter, employeeContractId, customerOrderId   
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.setString(11, filter)
								.list();
					}
				}
			}
		}
		return employeeorders;
	}
	
	/**
	 * Get a list of all Employeeorders fitting to the given filters ordered by employee, customer order, suborder.
	 * 
	 * @return List<Employeeorder> 
	 */
	public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, Long employeeContractId, Long customerOrderId) {
		List<Employeeorder> employeeorders = null;
		if (showInvalid == null || showInvalid == false) {
			Date now = new Date();
			if (filter == null || filter.trim().equals("")) {
				if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 01: only valid, no filter, no employeeContractId, no customerOrderId
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setDate(0, now)
								.setDate(1, now)
								.list();
					} else {
						// case 02: only valid, no filter, no employeeContractId, customerOrderId 
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setDate(1, now)
								.setDate(2, now)
								.list();
					}
				} else {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 03: only valid, no filter, employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"employeecontract.id = ? " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, employeeContractId)
								.setDate(1, now)
								.setDate(2, now)
								.list();
					} else {
						// case 04: only valid, no filter, employeeContractId, customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.setDate(2, now)
								.setDate(3, now)
								.list();
					}
				}
			} else {
				if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 05: only valid, filter, no employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"(upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setString(0, filter)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setDate(10, now)
								.setDate(11, now)
								.list();
					} else {
						// case 06: only valid, filter, no employeeContractId, customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.setDate(11, now)
								.setDate(12, now)
								.list();
					}
				} else {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 07: only valid, filter, employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"employeecontract.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, employeeContractId)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.setDate(11, now)
								.setDate(12, now)
								.list();
					} else {
						// case 08: only valid, filter, employeeContractId, customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"and fromdate <= ? " +
								"and (untildate = null " +
									"or untildate >= ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.setString(11, filter)
								.setDate(12, now)
								.setDate(13, now)
								.list();
					}
				}
			}
		} else {
			if (filter == null || filter.trim().equals("")) {
				if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 09: valid + invalid, no filter, no employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.list();
					} else {
						// case 10: valid + invalid, no filter, no employeeContractId, customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.list();
					}
				} else {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 11: valid + invalid, no filter, employeeContractId, no customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"employeecontract.id = ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, employeeContractId)
								.list();
					} else {
						// case 12: valid + invalid, no filter, employeeContractId, customerOrderId  
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.list();
					}
				}
			} else {
				if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 13: valid + invalid, filter, no employeeContractId, no customerOrderId   
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"upper(id) like ? " +
								"or upper(employeecontract.employee.sign) like ? " +
								"or upper(employeecontract.employee.firstname) like ? " +
								"or upper(employeecontract.employee.lastname) like ? " +
								"or upper(suborder.customerorder.sign) like ? " +
								"or upper(suborder.customerorder.description) like ? " +
								"or upper(suborder.customerorder.shortdescription) like ? " +
								"or upper(suborder.sign) like ? " +
								"or upper(suborder.description) like ? " +
								"or upper(suborder.shortdescription) like ? " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setString(0, filter)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.list();
					} else {
						// case 14: valid + invalid, filter, no employeeContractId, customerOrderId   
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.list();
					}
				} else {
					if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
						// case 15: valid + invalid, filter, employeeContractId, no customerOrderId   
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"employeecontract.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, employeeContractId)
								.setString(1, filter)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.list();
					} else {
						// case 16: valid + invalid, filter, employeeContractId, customerOrderId   
						employeeorders = getSession().createQuery("from Employeeorder eo where " +
								"suborder.customerorder.id = ? " +
								"and employeecontract.id = ? " +
								"and (upper(id) like ? " +
									"or upper(employeecontract.employee.sign) like ? " +
									"or upper(employeecontract.employee.firstname) like ? " +
									"or upper(employeecontract.employee.lastname) like ? " +
									"or upper(suborder.customerorder.sign) like ? " +
									"or upper(suborder.customerorder.description) like ? " +
									"or upper(suborder.customerorder.shortdescription) like ? " +
									"or upper(suborder.sign) like ? " +
									"or upper(suborder.description) like ? " +
									"or upper(suborder.shortdescription) like ?) " +
								"order by employeecontract.employee.sign asc, suborder.customerorder.sign asc, suborder.sign asc, fromdate asc")
								.setLong(0, customerOrderId)
								.setLong(1, employeeContractId)
								.setString(2, filter)
								.setString(3, filter)
								.setString(4, filter)
								.setString(5, filter)
								.setString(6, filter)
								.setString(7, filter)
								.setString(8, filter)
								.setString(9, filter)
								.setString(10, filter)
								.setString(11, filter)
								.list();
					}
				}
			}
		}
		return employeeorders;
	}
	
	
	
	/**
	 * Calls {@link EmployeeorderDAO#save(Employeeorder, Employee)} with {@link Employee} = null.
	 * @param eo
	 */
	public void save(Employeeorder eo) {
		save(eo, null);
	}
	
	/**
	 * Saves the given Employeeorder and sets creation-/update-user and creation-/update-date.
	 * 
	 * @param Employeeorder eo
	 */
	public void save(Employeeorder eo, Employee loginEmployee) {
		if (loginEmployee == null) {
			throw new RuntimeException("the login-user must be passed to the db");
		}
		Session session = getSession();
		java.util.Date creationDate = eo.getCreated();
		if (creationDate == null) {
			eo.setCreated(new java.util.Date());
			eo.setCreatedby(loginEmployee.getSign());
		} else {
			eo.setLastupdate(new java.util.Date());
			eo.setLastupdatedby(loginEmployee.getSign());
			Integer updateCounter = eo.getUpdatecounter();
			updateCounter = (updateCounter == null) ? 1 : updateCounter +1;
			eo.setUpdatecounter(updateCounter);
		}
		session.saveOrUpdate(eo);
		session.flush();
	}

	/**
	 * Deletes the given employee order.
	 * 
	 * @param long eoId
	 * 
	 * @return boolean
	 */
	public boolean deleteEmployeeorderById(long eoId) {
		List<Employeeorder> allEmployeeorders = getEmployeeorders();
		Employeeorder eoToDelete = getEmployeeorderById(eoId);
		boolean eoDeleted = false;
		
		for (Iterator iter = allEmployeeorders.iterator(); iter.hasNext();) {
			Employeeorder eo = (Employeeorder) iter.next();
			if(eo.getId() == eoToDelete.getId()) {
				boolean deleteOk = false;
				
				// check if related status reports exist - if so, no deletion possible				
				// TODO as soon as table STATUSREPORT is available...
				
				// check if related timereports exist
//				List<Timereport> timereports = timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndSuborderId(eo.getEmployeecontract().getId(), eo.getFromDate(), eo.getUntilDate(), eo.getSuborder().getId());
				List<Timereport> timereports = timereportDAO.getTimereportsByEmployeeOrderId(eo.getId());
				if (timereports == null || timereports.isEmpty()) {
					deleteOk = true;
				}
				
				if (deleteOk) {
					Session session = getSession();
					session.delete(eoToDelete);
					session.flush();
					eoDeleted = true;
				}
				break;
			}
		}
		
		return eoDeleted;
	}
}
