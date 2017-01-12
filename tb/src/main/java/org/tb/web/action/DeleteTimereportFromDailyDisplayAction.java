package org.tb.web.action;

import java.io.IOException;
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
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.bdom.Worklog;
import org.tb.bdom.WorklogMemory;
import org.tb.helper.AtlassianOAuthClient;
import org.tb.helper.JiraConnectionOAuthHelper;
import org.tb.helper.JiraSalatHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.persistence.WorklogDAO;
import org.tb.persistence.WorklogMemoryDAO;
import org.tb.web.form.ShowDailyReportForm;

/**
 * Action class for deletion of a timereport initiated from the daily display
 * 
 * @author oda
 *
 */
public class DeleteTimereportFromDailyDisplayAction extends DailyReportAction {
    
    private OvertimeDAO overtimeDAO;
    private CustomerorderDAO customerorderDAO;
    private TimereportDAO timereportDAO;
    private EmployeecontractDAO employeecontractDAO;
    private SuborderDAO suborderDAO;
    private EmployeeorderDAO employeeorderDAO;
    private PublicholidayDAO publicholidayDAO;
    private WorkingdayDAO workingdayDAO;
    private EmployeeDAO employeeDAO;
    private WorklogDAO worklogDAO;
    private WorklogMemoryDAO worklogMemoryDAO;
    
    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }
    public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
        this.workingdayDAO = workingdayDAO;
    }
    public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
        this.publicholidayDAO = publicholidayDAO;
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
    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }
    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }
    public void setWorklogDAO(WorklogDAO worklogDAO) {
        this.worklogDAO = worklogDAO;
    }
    public void setWorklogMemoryDAO(WorklogMemoryDAO worklogMemoryDAO) {
        this.worklogMemoryDAO = worklogMemoryDAO;
    }
    
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws IOException {
        
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }
        
        if (GenericValidator.isBlankOrNull(request.getParameter("trId")) ||  !GenericValidator.isLong(request.getParameter("trId"))) {
            return mapping.getInputForward();
        }
        
        long trId = Long.parseLong(request.getParameter("trId"));
        Timereport tr = timereportDAO.getTimereportById(trId);
        if (tr == null) {
            return mapping.getInputForward();
        }
        
        Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
        
        TimereportHelper th = new TimereportHelper();
        JiraConnectionOAuthHelper jcHelper = new JiraConnectionOAuthHelper(loginEmployee.getSign());
        
        
        // if timereport has a worklog, we have some work to do..
        Worklog salatWorklog = worklogDAO.getWorklogByTimereportID(tr.getId());
        if (salatWorklog != null) {
        	
        	String jiraAccessToken = loginEmployee.getJira_oauthtoken();
        	
        	// if JIRA is accessed for the first time or the access token is invalid:
        	if ( (jiraAccessToken == null && request.getParameter("oauth_verifier") == null) ||
        		 (jiraAccessToken != null && AtlassianOAuthClient.isValidAccessToken(jiraAccessToken) == false)	) {
        		// STEP 1: get a request token from JIRA and redirect user to JIRA login page
        		AtlassianOAuthClient.getRequestTokenAndSetRedirectToJira(response, GlobalConstants.SALAT_URL + "/do/DeleteTimereportFromDailyDisplay?trId=" + trId);//showDailyReport.jsp
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
        	
        	
        	//1: deleteWorklogMemories
        	deleteWorklogMemoriesForTimereport(tr);
        	
        	//2: delete Worklog
        	int jiraWorklogID = salatWorklog.getJiraWorklogID();
        	String jiraTicketKey = salatWorklog.getJiraTicketKey();
        	worklogDAO.deleteWorklog(salatWorklog);
        	
        	//3: delete the Timereport
        	if (!timereportDAO.deleteTimereportById(trId)) {
    	        return mapping.findForward("error");
            }
        	
        	//4: delete Jira-Worklog
            int responseDeleteWorklog = jcHelper.deleteWorklog(jiraWorklogID, jiraTicketKey);

            
            //4.1 in case of error - create WorklogMemory to delete the Jira-Worklog later 
            if (responseDeleteWorklog != 200) {
//            	request.getSession().setAttribute("deleteWorklogFailed", responseDeleteWorklog);
            	addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.jiraworklog.deleteerror", responseDeleteWorklog));
            	try {
            		JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, null, jiraTicketKey, jiraWorklogID, GlobalConstants.DELETE_WORKLOG);
            	} catch (Exception e) {
//            		request.getSession().setAttribute("createWorklogMemoryFailed", true);
            		addErrorAtTheBottom(request, errors, new ActionMessage("form.general.error.worklogmemoryfailed"));
            	}
//            	saveErrors(request, errors);
			}
        // if its a plain Timereport without Worklogs and stuff, just delete it..
        } else {        	
        	// make shure there are no worklogmemories for this timereport
        	deleteWorklogMemoriesForTimereport(tr);
        	
        	if (!timereportDAO.deleteTimereportById(trId)) {
        		return mapping.findForward("error");
        	}
        }
        
        //neu
        
        ShowDailyReportForm reportForm = (ShowDailyReportForm)request.getSession().getAttribute("reportForm");
        
        if (refreshTimereports(mapping, request, reportForm, customerorderDAO, timereportDAO, employeecontractDAO,
                suborderDAO, employeeorderDAO, publicholidayDAO, overtimeDAO) != true) {
            return mapping.findForward("error");
        } else {
            
            @SuppressWarnings("unchecked")
			List<Timereport> timereports = (List<Timereport>)request.getSession().getAttribute("timereports");
            request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
            request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
            request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
            //refresh workingday
            Workingday workingday;
            try {
                workingday = refreshWorkingday(mapping, reportForm, request, employeecontractDAO, workingdayDAO);
            } catch (Exception e) {
                return mapping.findForward("error");
            }
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
            request.getSession().setAttribute("quittingtime", th.calculateQuittingTime(workingday, request, "quittingtime"));
            if (employeecontract != null) {
                request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
                request.getSession().setAttribute("currentEmployeeContract", employeecontract);
            }
            return mapping.findForward("success");
        }
        
    }
	private void deleteWorklogMemoriesForTimereport(Timereport tr) {
		List<WorklogMemory> wml = worklogMemoryDAO.getAllWorklogMemory();
		for (WorklogMemory wm: wml) {
			if (wm.getTimereport().getId() == tr.getId()) {
				worklogMemoryDAO.delete(wm);
			}
		}
	}
    
	@Override
	protected boolean isAllowedForRestrictedUsers() {
		return true;
	}
}
