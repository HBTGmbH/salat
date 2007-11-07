package org.tb.web.action.admin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Timereport;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.MD5Util;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowAdminOptionsForm;

public class ShowAdminOptionsAction extends LoginRequiredAction {

	private TimereportDAO timereportDAO;
	private EmployeecontractDAO employeecontractDAO;
	private SuborderDAO suborderDAO;
	private EmployeeorderDAO employeeorderDAO;
	private EmployeeDAO employeeDAO;
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	
	@Override
	protected ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
		request.getSession().removeAttribute("setemployeeorderresults");
		request.getSession().removeAttribute("unassignedreports");
		request.getSession().removeAttribute("problems");
		
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("SetEmployeeOrderInTimereports"))) {
			
			Date start = new Date();
			List<String> problems = new ArrayList<String>();
			
			List<Timereport> timereports = timereportDAO.getOrderedTimereports();
			List<Timereport> unassignedTimereports = new ArrayList<Timereport>();
			int total = timereports.size();
			int unassignable = 0;
			int updated = 0;
			int assigned = 0;
			for (Timereport timereport : timereports) {
				if (timereport.getEmployeeorder() == null) {
					List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(
							timereport.getEmployeecontract().getId() ,
							timereport.getSuborder().getId(),
							timereport.getReferenceday().getRefdate());
					if (employeeorders != null && !employeeorders.isEmpty()) {
						
						if (employeeorders.size() > 1) {
							String problem = "TR [tr:"+timereport.getId()+" | ec:"+timereport.getEmployeecontract().getId()+" | emp:"+
								timereport.getEmployeecontract().getEmployee().getSign() +" | so:"+timereport.getSuborder().getId()+
								" | order:"+timereport.getSuborder().getCustomerorder().getSign()+" / "+timereport.getSuborder().getSign()+
								" | date:"+timereport.getReferenceday().getRefdate()+"]";
							problems.add(problem);
							for (Employeeorder employeeorder : employeeorders) {
								problem = "EO [eo:"+employeeorder.getId()+" | ec:"+employeeorder.getEmployeecontract().getId()+" | emp:"+
									employeeorder.getEmployeecontract().getEmployee().getSign()+
									" | so:"+employeeorder.getSuborder().getId()+" | order:"+employeeorder.getSuborder().getCustomerorder().getSign()+
									" / "+employeeorder.getSuborder().getSign()
									+" | from:"+employeeorder.getFromDate()+
									" | until:"+employeeorder.getUntilDate()+"]";
								problems.add(problem);
							}
							problems.add("---------------------------------------------------------------");
							unassignable++;
							unassignedTimereports.add(timereport);
						} else {
							timereport.setEmployeeorder(employeeorders.get(0));
						
						// check if selection has no errors
//						if (timereport.getReferenceday().getRefdate().before(employeeorder.getFromDate())) {
//							throw new RuntimeException("refdate(tr) before fromdate(eo) trId: "+timereport.getId()+" eoId"+employeeorder.getId()+
//									" refdate: "+timereport.getReferenceday().getRefdate()+" fromdate: "+employeeorder.getFromDate());
//						}
//						if (employeeorder.getUntilDate()!=null && timereport.getReferenceday().getRefdate().after(employeeorder.getUntilDate())) {
//							throw new RuntimeException("refdate(tr) after untildate(eo) trId: "+timereport.getId()+" eoId"+employeeorder.getId()+
//									" refdate: "+timereport.getReferenceday().getRefdate()+" untildate: "+employeeorder.getUntilDate());
//						}
						
						// save timereport
						Employee saveEmployee = new Employee();
						saveEmployee.setSign("system");
						timereportDAO.save(timereport, saveEmployee, false);
						
						updated++;
						}
					} else {
						unassignable++;
						unassignedTimereports.add(timereport);
					}
				} else {
					assigned++;
				}
			}
//			System.out.println("total: "+total+" with: "+withEO+" without: "+withoutEO+" unassigned: "+unassignable );
			
			Date end = new Date();
			Long millis = end.getTime() - start.getTime();
			Long sec = millis/1000;
			Long min = sec/60;
			sec = sec%60;
			
			request.getSession().setAttribute("setemployeeorderresults", "result:  total reports: "+total+" updated: "+updated+" unassignable: "+unassignable+" unaltered: "+assigned +"    duration: "+min+":"+sec+" minutes");
			request.getSession().setAttribute("unassignedreports", unassignedTimereports);
			request.getSession().setAttribute("problems", problems);
			
			return mapping.findForward("success");
		}
		
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("convertPasswordsToMD5"))) {
			
			List<Employee> employees = employeeDAO.getEmployees();
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
			
			for (Employee employee : employees) {
				if (!employee.equals(loginEmployee)) {
					employee.changePassword(employee.getPassword());
					employeeDAO.save(employee, loginEmployee);
				}
			}
			
			
			return mapping.findForward("success");
		}
		
		
		if (request.getParameter("task") == null) {
			return mapping.findForward("success");
		}
		
		return mapping.findForward("error");
	}

}
