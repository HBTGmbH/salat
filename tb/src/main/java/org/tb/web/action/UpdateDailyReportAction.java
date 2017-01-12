package org.tb.web.action;

import java.io.IOException;
import java.sql.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.bdom.Worklog;
import org.tb.helper.AtlassianOAuthClient;
import org.tb.helper.JiraConnectionOAuthHelper;
import org.tb.helper.JiraSalatHelper;
import org.tb.helper.TimereportHelper;
import org.tb.helper.VacationViewer;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.ProjectIDDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.persistence.WorklogDAO;
import org.tb.persistence.WorklogMemoryDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.ShowDailyReportForm;
import org.tb.web.form.UpdateDailyReportForm;

/**
 * action class for updating a timereport directly from daily display
 * 
 * @author oda
 *
 */
public class UpdateDailyReportAction extends DailyReportAction {
    
    private SuborderDAO suborderDAO;
    private CustomerorderDAO customerorderDAO;
    private TimereportDAO timereportDAO;
    private PublicholidayDAO publicholidayDAO;
    private WorkingdayDAO workingdayDAO;
    private EmployeeorderDAO employeeorderDAO;
    private OvertimeDAO overtimeDAO;
    private EmployeeDAO employeeDAO;
    private EmployeecontractDAO employeecontractDAO;
    private WorklogDAO worklogDAO;
    private ProjectIDDAO projectIDDAO;
    private WorklogMemoryDAO worklogMemoryDAO;
    
    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }
    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }
    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }
    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }
    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }
    public TimereportDAO getTimereportDAO() {
        return timereportDAO;
    }
    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }
    public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
        this.publicholidayDAO = publicholidayDAO;
    }
    public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
        this.workingdayDAO = workingdayDAO;
    }
    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }
    public void setWorklogDAO(WorklogDAO worklogDAO) {
        this.worklogDAO = worklogDAO;
    }
    public void setProjectIDDAO(ProjectIDDAO projectIDDAO) {
        this.projectIDDAO = projectIDDAO;
    }
    public void setWorklogMemoryDAO(WorklogMemoryDAO worklogMemoryDAO) {
        this.worklogMemoryDAO = worklogMemoryDAO;
    }
    
    /* (non-Javadoc)
     * @see org.tb.web.action.LoginRequiredAction#executeAuthenticated(org.apache.struts.action.ActionMapping, org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws IOException {
        UpdateDailyReportForm reportForm = (UpdateDailyReportForm)form;
        
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }
        
        if (request.getParameter("trId") != null) {
            long trId = Long.parseLong(request.getParameter("trId"));
            Timereport tr = timereportDAO.getTimereportById(trId);
            
            int previousDurationhours = tr.getDurationhours(); 
            int previousDurationminutes = tr.getDurationminutes();
            String previousTaskdescription = tr.getTaskdescription();
            Date theDate = tr.getReferenceday().getRefdate();
            Employeecontract ec = tr.getEmployeecontract();
            
            ActionMessages errorMessages = validateFormData(request, reportForm, theDate, tr);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }
            
            tr.setTaskdescription(reportForm.getComment());
            tr.setDurationhours(new Integer(reportForm.getSelectedDurationHour()));
            tr.setDurationminutes(new Integer(reportForm.getSelectedDurationMinute()));
            tr.setCosts(reportForm.getCosts());
            tr.setTraining(reportForm.getTraining());
            
            Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
            
            //check if report's order is vacation but not Overtime compensation
            if (tr.getSuborder().getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                    && !tr.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                //fill VacationView with data
                Employeeorder vacationOrder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(ec.getId(), tr.getSuborder().getId(), theDate);
                VacationViewer vacationView = new VacationViewer(ec);
                vacationView.setSuborderSign(vacationOrder.getSuborder().getSign());
                if (vacationOrder.getDebithours() != null) {
                    vacationView.setBudget(vacationOrder.getDebithours());
                } else { //should not happen since debit hours of yearly vacation order is generated automatically when the order is created
                    vacationOrder.setDebithours(vacationOrder.getEmployeecontract().getVacationEntitlement() * vacationOrder.getEmployeecontract().getDailyWorkingTime());
                    vacationView.setBudget(vacationOrder.getDebithours());
                }
                List<Timereport> timereports = timereportDAO.getTimereportsBySuborderIdAndEmployeeContractId(vacationOrder.getSuborder().getId(), ec.getId());
                for (Timereport timereport : timereports) {
                    if (tr.getId() != timereport.getId()) {
                        vacationView.addVacationHours(timereport.getDurationhours());
                        vacationView.addVacationMinutes(timereport.getDurationminutes());
                    }
                }
                vacationView.addVacationHours(tr.getDurationhours());
                vacationView.addVacationMinutes(tr.getDurationminutes());
                //check if current timereport would overrun vacation budget of corresponding year of suborder
                if (vacationView.getExtended()) {
                    request.getSession().setAttribute("vacationBudgetOverrun", true);
                    return mapping.findForward("success");
                } else {
                    request.getSession().setAttribute("vacationBudgetOverrun", false);
                    timereportDAO.save(tr, loginEmployee, true);
                }
                
            } else {
                // save updated report
                request.getSession().setAttribute("vacationBudgetOverrun", false);
                timereportDAO.save(tr, loginEmployee, true);
            }
            
            // check if Durationhours and/or Durationminutes have been adjusted for this save.  
            boolean newTaskdescription = !previousTaskdescription.equals(tr.getTaskdescription());
            boolean newTime = tr.getDurationhours() != previousDurationhours || tr.getDurationminutes() != previousDurationminutes;

            
            if (newTime || newTaskdescription) {
                // need to check if order of timereport has a jira-project-id attached
                if (!projectIDDAO.getProjectIDsByCustomerorderID(tr.getSuborder().getCustomerorder().getId()).isEmpty()) {
                	
                	
                	
                	
                	
                	
                	String jiraAccessToken = loginEmployee.getJira_oauthtoken();
                	
                	// if JIRA is accessed for the first time or the access token is invalid
                	if ( (jiraAccessToken == null && request.getParameter("oauth_verifier") == null) ||
                		 (jiraAccessToken != null && AtlassianOAuthClient.isValidAccessToken(jiraAccessToken) == false)	) {
                		// STEP 1: get a request token from JIRA and redirect user to JIRA login page
                		AtlassianOAuthClient.getRequestTokenAndSetRedirectToJira(response, GlobalConstants.SALAT_URL + "/do/UpdateDailyReport?trId=" + trId);
                		return null;
                	} else {
                		AtlassianOAuthClient.setAccessToken(jiraAccessToken);
                	}
                	
                	// STEP 2: JIRA returned a verifier code. Now swap the request token and the verifier with access token 
                	String oauthVerifier = request.getParameter("oauth_verifier");
                	if (oauthVerifier != null) {
                		if (oauthVerifier.equals("denied")) {
                			addErrorAtTheBottom(request, errors, new ActionMessage("oauth.error.denied"));
                			return mapping.getInputForward();
                		} else {
                			String accessToken = AtlassianOAuthClient.swapRequestTokenForAccessToken(oauthVerifier, employeeDAO, loginEmployee);
                    		if (accessToken == null) return mapping.findForward("error");
                		}
                	}
                	
                	
                	
                	
                	
                	
                	
                	
             	
                	JiraConnectionOAuthHelper jcHelper = new JiraConnectionOAuthHelper(loginEmployee.getSign());
                	
                    //need to check if worklog already exists (only applies to projects that obtained a jira-project-id after timereports have been stored)
                    Worklog salatWorklog = worklogDAO.getWorklogByTimereportID(tr.getId());
                    String jiraKey = tr.getTicket().getJiraTicketKey();
                    String jiraProjectID = projectIDDAO.getProjectIDsByCustomerorderID(tr.getSuborder().getCustomerorder().getId()).get(0).getJiraProjectID();
                    jiraKey = jiraProjectID + "-" + jiraKey;
                    
                    if (salatWorklog != null) {
                    		
                    	int responseUpdateWorklog = jcHelper.updateWorklog(tr, jiraKey, salatWorklog.getJiraWorklogID());
						
                    	//if Worklog not found/has been deleted - try to create a new one
    	            	if (responseUpdateWorklog == 404) {
    	            		int[] create_status = jcHelper.createWorklog(tr, jiraKey);
    						if (create_status[0] != 200) {
//    							request.getSession().setAttribute("updateWorklogFailed", create_status[0]);
    							addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.updateerror", create_status[0]));
    							try {
    								JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, jiraKey, 0, GlobalConstants.CREATE_WORKLOG);
    		                	} catch (Exception e) {
//    		                		request.getSession().setAttribute("createWorklogMemoryFailed", true);
    		                		addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.worklogmemoryfailed"));
    		                	}
    						} else {
    							salatWorklog.setJiraWorklogID(create_status[1]);
    							salatWorklog.setType("updated");
    							salatWorklog.setUpdatecounter(salatWorklog.getUpdatecounter() + 1);
    						}
    	            	} else if (responseUpdateWorklog != 200) {
//    						request.getSession().setAttribute("updateWorklogFailed", responseUpdateWorklog);
    	            		addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.updateerror", responseUpdateWorklog));
    	            		try {
    	            			JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, jiraKey, salatWorklog.getJiraWorklogID(), GlobalConstants.UPDATE_WORKLOG);
    	                	} catch (Exception e) {
//    	                		request.getSession().setAttribute("createWorklogMemoryFailed", true);
    	                		addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.worklogmemoryfailed"));
    	                	}
    					} 
                    	
                    	if (newTime || newTaskdescription) {
            				salatWorklog.setType("updated");
            				salatWorklog.setUpdatecounter(salatWorklog.getUpdatecounter() + 1);
						}
                    } else {
                    	int[] responseCreateWorklog = jcHelper.createWorklog(tr, jiraKey);
                    	if (responseCreateWorklog[0] != 200) {
//                    		request.getSession().setAttribute("createWorklogFailed", responseCreateWorklog[0]);
                    		addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.createerror", responseCreateWorklog[0]));
    	                	try {
    	                		JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, tr, jiraKey, 0, GlobalConstants.CREATE_WORKLOG);
    	                	} catch (Exception e) {
//    	                		request.getSession().setAttribute("createWorklogMemoryFailed", true);
    	                		addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.worklogmemoryfailed"));
    	                	}
    					}
        				salatWorklog = new Worklog();
        				salatWorklog.setJiraWorklogID(responseCreateWorklog[1]);
        				salatWorklog.setTimereport(tr);
        				salatWorklog.setType("created");
        				salatWorklog.setUpdatecounter(0);
                    }
                    
                }
//                saveErrors(request, errors);
            }
            TimereportHelper th = new TimereportHelper();
            if (tr.getStatus().equalsIgnoreCase(GlobalConstants.TIMEREPORT_STATUS_CLOSED) && loginEmployee.getStatus().equalsIgnoreCase("adm")) {
                // recompute overtimeStatic and store it in employeecontract
                double otStatic = th.calculateOvertime(ec.getValidFrom(), ec.getReportAcceptanceDate(),
                        ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
                ec.setOvertimeStatic(otStatic / 60.0);
                employeecontractDAO.save(ec, loginEmployee);
            }
            
            // get updated list of timereports from DB
            ShowDailyReportForm showDailyReportForm = new ShowDailyReportForm();
            showDailyReportForm.setDay((String)request.getSession().getAttribute("currentDay"));
            showDailyReportForm.setMonth((String)request.getSession().getAttribute("currentMonth"));
            showDailyReportForm.setYear((String)request.getSession().getAttribute("currentYear"));
            showDailyReportForm.setLastday((String)request.getSession().getAttribute("lastDay"));
            showDailyReportForm.setLastmonth((String)request.getSession().getAttribute("lastMonth"));
            showDailyReportForm.setLastyear((String)request.getSession().getAttribute("lastYear"));
            showDailyReportForm.setEmployeeContractId(ec.getId());
            showDailyReportForm.setView((String)request.getSession().getAttribute("view"));
            showDailyReportForm.setOrder((String)request.getSession().getAttribute("currentOrder"));
            showDailyReportForm.setStartdate((String)request.getSession().getAttribute("startdate"));
            showDailyReportForm.setEnddate((String)request.getSession().getAttribute("enddate"));
            
            Long currentSuborderId = (Long)request.getSession().getAttribute("currentSuborderId");
            if (currentSuborderId == null || currentSuborderId == 0) {
                currentSuborderId = -1l;
            }
            showDailyReportForm.setSuborderId(currentSuborderId);
            
            refreshTimereports(mapping,
                    request,
                    showDailyReportForm,
                    customerorderDAO,
                    timereportDAO,
                    employeecontractDAO,
                    suborderDAO,
                    employeeorderDAO,
                    publicholidayDAO,
                    overtimeDAO);
            @SuppressWarnings("unchecked")
			List<Timereport> timereports = (List<Timereport>)request.getSession().getAttribute("timereports");
            
            request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
            
            Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(tr.getReferenceday().getRefdate(), ec.getId());
            
            // save values from the data base into form-bean, when working day != null
            if (workingday != null) {
                
                //show break time, quitting time and working day ends on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", true);
                
                showDailyReportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
                showDailyReportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
                showDailyReportForm.setSelectedBreakHour(workingday.getBreakhours());
                showDailyReportForm.setSelectedBreakMinute(workingday.getBreakminutes());
            } else {
                
                //show�t break time, quitting time and working day ends on the showdailyreport.jsp
                request.getSession().setAttribute("visibleworkingday", false);
                
                showDailyReportForm.setSelectedWorkHourBegin(0);
                showDailyReportForm.setSelectedWorkMinuteBegin(0);
                showDailyReportForm.setSelectedBreakHour(0);
                showDailyReportForm.setSelectedBreakMinute(0);
            }
            
            request.getSession().setAttribute("quittingtime", th.calculateQuittingTime(workingday, request, "quittingtime"));
            
            //refresh overtime
            refreshVacationAndOvertime(request, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
            
            return mapping.findForward("success");
        }
        
        return mapping.findForward("error");
    }
    
    /**
     * validates the form data (syntax and logic)
     * 
     * @param request
     * @param reportForm
     * @param theDate - sql date
     * @param theTimereport
     * @return
     */
    private ActionMessages validateFormData(HttpServletRequest request,
            UpdateDailyReportForm reportForm,
            Date theDate,
            Timereport theTimereport) {
        
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }
        
        // if sort of report is not 'W' reports are only allowed for workdays
        // e.g., vacation cannot be set on a Sunday
        if (!theTimereport.getSortofreport().equals("W")) {
            boolean valid = !DateUtils.isSatOrSun(theDate);
            
            // checks for public holidays
            if (valid) {
                String publicHoliday = publicholidayDAO.getPublicHoliday(theDate);
                if (publicHoliday != null && publicHoliday.length() > 0) {
                    valid = false;
                }
            }
            
            if (!valid) {
                errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.invalidday"));
            }
        }
        
        if (theTimereport.getSortofreport().equals("W")) {
            // check costs format		
            if (!GenericValidator.isDouble(reportForm.getCosts().toString()) ||
                    !GenericValidator.isInRange(reportForm.getCosts(),
                            0.0, GlobalConstants.MAX_COSTS)) {
                errors.add("costs", new ActionMessage("form.timereport.error.costs.wrongformat"));
            }
        }
        
        // check comment length
        if (!GenericValidator.maxLength(reportForm.getComment(), GlobalConstants.COMMENT_MAX_LENGTH)) {
            errors.add("comment", new ActionMessage("form.timereport.error.comment.toolarge"));
        }
        
        // check if comment is necessary
        Boolean commentnecessary = theTimereport.getSuborder().getCommentnecessary();
        if (commentnecessary && (reportForm.getComment() == null || reportForm.getComment().trim().equals(""))) {
            errors.add("comment", new ActionMessage("form.timereport.error.comment.necessary"));
        }
        
        saveErrors(request, errors);
        
        return errors;
    }
    
    @Override
    protected boolean isAllowedForRestrictedUsers() {
    	return true;
    }
}
