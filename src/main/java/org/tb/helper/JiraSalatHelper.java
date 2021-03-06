package org.tb.helper;

import org.apache.commons.lang.NullArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.persistence.TicketDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorklogDAO;
import org.tb.persistence.WorklogMemoryDAO;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mgo
 */
public class JiraSalatHelper {
    private static final Logger LOG = LoggerFactory.getLogger(JiraSalatHelper.class);

    /**
     * check if for the given orderID, one or more ProjectIDs exist.
     * If so, set known Jira-Ticket-Keys into the session, so that the jsp can display them in a dropdown menu.
     */
    public static void setJiraTicketKeysForSuborder(HttpServletRequest request, TicketDAO ticketDAO, long suborderID) {

        List<String> jiraTicketKeys = new ArrayList<String>();
        List<Ticket> tickets = ticketDAO.getTicketsBySuborderID(suborderID);

        if (!tickets.isEmpty()) {
            for (Ticket t : tickets) {
                jiraTicketKeys.add(t.getJiraTicketKey());
            }
        }
        request.getSession().setAttribute("jiraTicketKeys", jiraTicketKeys);
    }

    /**
     * @param operation        GlobalConstants.CREATE_WORKLOG, GlobalConstants.UPDATE_WORKLOG or GlobalConstants.DELETE_WORKLOG
     */
    public static void saveFailedWorklog(WorklogMemoryDAO worklogMemoryDAO, TimereportDAO timereportDAO, Timereport tr, String issueID, int worklogID, int operation) {
        WorklogMemory worklogMemory = new WorklogMemory();
        worklogMemory.setOperation(operation);
        worklogMemory.setTimereport(tr);
        worklogMemory.setIssueID(issueID);
        worklogMemory.setWorklogID(worklogID);
        worklogMemoryDAO.save(worklogMemory);
    }

    public static void executeFailedWorklogs(WorklogMemoryDAO worklogMemoryDAO, TimereportDAO timereportDAO, WorklogDAO worklogDAO) {

        if (worklogMemoryDAO != null && timereportDAO != null && worklogDAO != null) {

            String[] operationNames = {"create", "update", "delete"};

            JiraConnectionOAuthHelper jcHelper = new JiraConnectionOAuthHelper("dummy");
            List<WorklogMemory> allWorklogs = worklogMemoryDAO.getAllWorklogMemory();

            if (!allWorklogs.isEmpty()) {
                for (WorklogMemory worklogMemory : allWorklogs) {

                    int operation = worklogMemory.getOperation();
                    Employee employee = worklogMemory.getTimereport().getEmployeecontract().getEmployee();
                    String accessToken = employee.getJira_oauthtoken();

                    if (accessToken != null) {
                        AtlassianOAuthClient.setAccessToken(accessToken);
                        String sign = employee.getSign();
                        jcHelper.setSign(sign);


                        try {
                            Timereport timereport = null;
                            if (worklogMemory.getTimereport() != null) {
                                timereport = timereportDAO.getTimereportById(worklogMemory.getTimereport().getId());
                            }

                            switch (operation) {

                                case GlobalConstants.CREATE_WORKLOG: //1
                                    int[] responseCreate = jcHelper.createWorklog(timereport, worklogMemory.getIssueID());
                                    if (responseCreate[0] == 200) {
                                        Worklog worklog = getWorklog(worklogMemory, worklogDAO);
                                        if (worklog != null) {
                                            worklog.setJiraWorklogID(responseCreate[1]);
                                        } else {
                                            worklog = new Worklog();
                                            worklog.setJiraWorklogID(responseCreate[1]);
                                            worklog.setJiraTicketKey(worklogMemory.getIssueID());
                                            worklog.setTimereport(timereport);
                                            worklog.setType("created");
                                            worklog.setUpdatecounter(0);
                                        }
                                        worklogDAO.save(worklog);
                                        LOG.info("Successfully created Jira-Worklog - " + worklog.getJiraWorklogID());
                                        deleteWorklogMemory(worklogMemoryDAO, worklogMemory);
                                    } else {
                                        throw new RuntimeException("Create Jira-Worklog failed: status code " + responseCreate[0]);
                                    }
                                    break;

                                case GlobalConstants.UPDATE_WORKLOG: //2
                                    int responseUpdate = jcHelper.updateWorklog(timereport, worklogMemory.getIssueID(), worklogMemory.getWorklogID());
                                    if (responseUpdate == 404) {
                                        LOG.debug("Update Jira-Worklog failed: Worklog not found. Trying to create new Worklog..");
                                        responseCreate = jcHelper.createWorklog(timereport, worklogMemory.getIssueID());
                                        if (responseCreate[0] == 200) {
                                            Worklog worklog = getWorklog(worklogMemory, worklogDAO);
                                            if (worklog != null) {
                                                worklog.setJiraWorklogID(responseCreate[1]);
                                            } else {
                                                worklog = new Worklog();
                                                worklog.setJiraWorklogID(responseCreate[1]);
                                                worklog.setJiraTicketKey(worklogMemory.getIssueID());
                                                worklog.setTimereport(timereport);
                                                worklog.setType("updated");
                                                worklog.setUpdatecounter(1);
                                            }
                                            worklogDAO.save(worklog);
                                            LOG.debug("Successfully updated Jira-Worklog - " + worklog.getJiraWorklogID());
                                            deleteWorklogMemory(worklogMemoryDAO, worklogMemory);
                                        } else {
                                            LOG.error("New Worklog could not be created: status code " + responseCreate[0]);
                                            throw new RuntimeException(""); //no more infos needed
                                        }
                                    } else if (responseUpdate == 200) {
                                        worklogMemoryDAO.delete(worklogMemory);
                                        LOG.debug("Successfully updated Worklog - " + worklogMemory.getWorklogID());
                                    } else {
                                        throw new RuntimeException("Update Jira-Worklog failed: status code " + responseUpdate);
                                    }
                                    break;

                                case GlobalConstants.DELETE_WORKLOG: //3
                                    if (worklogMemory.getWorklogID() != 0) {
                                        int responseDelete = jcHelper.deleteWorklog(worklogMemory.getWorklogID(), worklogMemory.getIssueID());
                                        if (responseDelete == 200) {
                                            deleteWorklogMemory(worklogMemoryDAO, worklogMemory);
                                            LOG.debug("Successfully deleted Worklog - " + worklogMemory.getWorklogID());
                                        } else {
                                            throw new RuntimeException("Delete Worklog from Jira failed: status code " + responseDelete);
                                        }
                                    } else {
                                        throw new IllegalArgumentException("worklogMemory.worklogID must not be 0. Please contact some Salat-Dev!");
                                    }
                                    break;

                                default:
                                    break;
                            }
                        } catch (IOException e) {
                            LOG.error("Failed to " + operationNames[operation - 1] + " worklog.WorklogMemoryId: " + worklogMemory.getId() + "\n -> " + e.getMessage());
                        }


                    }

                }
            } else {
                LOG.debug(JiraSalatHelper.class.getName(), "WorklogMemory is empty. Thats a good thing! ;-)");
            }
        } else {
            throw new NullArgumentException("Passed arguments");
        }
    }

    private static Worklog getWorklog(WorklogMemory worklogMemory, WorklogDAO worklogDAO) throws IllegalArgumentException {

        if (worklogMemory.getTimereport() != null) {
            return worklogDAO.getWorklogByTimereportID(worklogMemory.getTimereport().getId());
        } else if (worklogMemory.getWorklogID() != 0) {
            return worklogDAO.getWorklogByJiraWorklogID(worklogMemory.getWorklogID());
        } else {
            throw new IllegalArgumentException("WorklogMemory object lacks attributes!");
        }
    }

    private static void deleteWorklogMemory(WorklogMemoryDAO worklogMemoryDAO, WorklogMemory worklogMemory) {
        if (!worklogMemoryDAO.delete(worklogMemory)) {
            throw new RuntimeException("Could not delete WorklogMemory: " + worklogMemory.getId() + " after execution");
        }
    }

}
