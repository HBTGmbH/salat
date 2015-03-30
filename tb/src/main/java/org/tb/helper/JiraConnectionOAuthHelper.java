package org.tb.helper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import net.oauth.OAuthMessage;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.tb.GlobalConstants;
import org.tb.bdom.Timereport;
import org.tb.web.action.InvalidAccessTokenException;

/**
 * Helper class for performing OAuth requests to Jira 
 * 
 * @author mgo
 *
 */

public class JiraConnectionOAuthHelper {
	
	private String user_sign;
	
	public JiraConnectionOAuthHelper(String sign) {
		user_sign = sign;
	}
	
	/**
	 * Sets the assignee for the current worklog
	 * @param key path of the issue
	 * @param user the new assignee
	 * @return status code of the response<br>
	 * 	200 - OK<br>
	 * 	400 - Returned if there is a problem with the received user representation<br>
	 * 	401 - Returned if the calling user does not have permission to assign the issue<br>
	 * 	403 - Returned if the authorization has failed<br>
	 *	404 - Returned if either the issue or the user does not exist<br>
	 * @throws IOException 
	 * @throws URISyntaxException 
	 */
	private int setAssignee(String key) throws URISyntaxException, IOException {
		String resource = GlobalConstants.JIRA_URL + "/rest/api/2/issue/" + key + "/assignee";
		try {
			JSONObject json = new JSONObject();
			json.put("name", user_sign);
			HttpResponse r = AtlassianOAuthClient.makeAuthenticatedRequest(OAuthMessage.PUT, resource, json);
			return r.status_code;
		} catch (JSONException e) {
			return 400;
		}
	}
	
	/**
	 * Creates a json object to create a new worklog
	 * @param tr timereport from Salat
	 * @param resource path of the resource
	 * @return Json Object with the informations of the timereport from Salat
	 * @throws JSONException
	 */
	private JSONObject createJsonWorklog(Timereport tr, String resource) throws JSONException {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		
		JSONObject obj = new JSONObject();
		
		JSONObject author = new JSONObject();
		author.put("self", GlobalConstants.JIRA_URL + "/rest/api/2/user?username=" + user_sign);
		author.put("name", user_sign);
		author.put("displayName", user_sign);
		author.put("active", true);
		
		obj.put("self", GlobalConstants.JIRA_URL + resource);
		obj.put("author", author);
		obj.put("updateAuthor", author);
		obj.put("comment", tr.getTaskdescription());
		obj.put("started", dateFormat.format(tr.getReferenceday().getRefdate()));
		obj.put("timeSpent", tr.getDurationhours() + "h " + tr.getDurationminutes() + "m");
		
		return obj;
	}
	
	/**
	 * Checks if the given project id exists in jira
	 * @param id project id
	 * @return status code of the response<br>
	 * 	200 - OK<br>
	 * 	404 - Returned if the project is not found or the calling user does not have permission<br>
	 * 	500 - ClientHandlerException<br>
	 */
	public int checkJiraProjectID(String id) {
		try {
			String resource = "/rest/api/2/project/" + id;
			return AtlassianOAuthClient.makeAuthenticatedRequest(OAuthMessage.GET, GlobalConstants.JIRA_URL + resource, null).status_code;
		} catch (Exception e) {
			return 500;
		}
	}
	
	/**
	 * Checks if the given ticket id exists in jira
	 * @param id - ticket id
	 * @return status code of the response<br>
	 * 	200 - OK<br>
	 * 	404 - Returned if the issue is not found or the calling user does not have permission<br>
	 * 	500 - ClientHandlerException<br>
	 */
	public int checkJiraTicketID(String id) {
		try {
			String resource = "/rest/api/2/issue/" + id;
			return AtlassianOAuthClient.makeAuthenticatedRequest(OAuthMessage.GET, GlobalConstants.JIRA_URL + resource, null).status_code;
		} catch (Exception e) {
			return 500;
		} 
	}
	
	/**
	 * Creates a new worklog
	 * @param tr - timereport from Salat
	 * @param jiraKey - the id of the ticket in jira
	 * @return status code of the response [0] and the id of the created worklog [1]<br>
	 * 	200 - OK<br>
	 * 	400 - Returned if the input is invalid (e.g. missing required fields, invalid values, and so forth)<br>
	 * 	401 - Returned if the calling user does not have permission to assign the issue (setAssignee)<br>
	 *  402 - Returned if there is a problem with the received user representation (setAssignee)<br>
	 * 	403 - Returned if the calling user does not have permission to add the worklog<br>
	 *	404 - Returned if either the issue or the user does not exist (setAssignee)<br>
	 * 	500 - ClientHandlerException<br>
	 * @throws IOException 
	 * @throws InvalidAccessTokenException 
	 */
	public int[] createWorklog(Timereport tr, String jiraKey) throws IOException { 
		
		int[] result = new int[2]; 
		try {
			int assigneeResult = setAssignee(jiraKey);
			if (assigneeResult != 200) {
				if (assigneeResult == 400) assigneeResult = 402; //weil createWorklog auch 400 liefern kann
				result[0] = assigneeResult;
				return result;
			}
			String resource = "/rest/api/2/issue/" + jiraKey + "/worklog";
			JSONObject jsonWorklog = createJsonWorklog(tr, resource);
			HttpResponse r = AtlassianOAuthClient.makeAuthenticatedRequest(OAuthMessage.POST,  GlobalConstants.JIRA_URL + resource, jsonWorklog);
			result[0] = r.status_code;
			if (r.message_body != null) {
				result[1] = Integer.parseInt(new JSONObject(r.message_body).get("id").toString());
			}
		} catch (JSONException e) {
			result[0] = 400;
		} catch (Exception e) {
			result[0] = 500;
		}
		return result;
	}
	
	
	/**
	 * Updates the given worklog
	 * @param tr - timereport
	 * @param jiraKey - the id of the ticket in jira
	 * @param worklogId - id of the worklog
	 * @return status code of the response<br>
	 * 	200 - OK<br>
	 * 	400 - Returned if the input is invalid (e.g. missing required fields, invalid values, and so forth)<br>
	 * 	401 - Returned if the calling user does not have permission to assign the issue<br>
	 * 	402 - Returned if there is a problem with the received user representation<br>
	 * 	403 - Returned if the calling user does not have permission to delete the worklog<br>
	 *	404 - Returned if either the issue or the user does not exist<br>
	 * 	500 - ClientHandlerException<br>
	 * @throws IOException 
	 * @throws InvalidAccessTokenException 
	 */
	public int updateWorklog(Timereport tr, String jiraKey, int worklogId) throws IOException {
		
		try {
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
			String resource = "/rest/api/2/issue/" + jiraKey + "/worklog/" + worklogId;
			String worklogComment = tr.getTaskdescription();
			JSONObject json = new JSONObject();
			json.put("timeSpent", tr.getDurationhours() + "h " + tr.getDurationminutes() + "m");
			json.put("comment", worklogComment);
			json.put("started", dateFormat.format(tr.getReferenceday().getRefdate()));
			return AtlassianOAuthClient.makeAuthenticatedRequest(OAuthMessage.PUT, GlobalConstants.JIRA_URL + resource, json).status_code;
		} catch (JSONException e) {
			return 400;
		} catch (Exception e) {
			return 500;
		}
	}
	
	/**
	 * Deletes the given worklog
	 * @param worklogId - id of the worklog
	 * @param jiraKey - the id of the ticket in jira
	 * @return status code of the response<br>
	 * 	200 - Returned if worklog was successfully deleted or was not found<br>
	 * 	400 - Returned if the input is invalid (e.g. missing required fields, invalid values, and so forth)<br>
	 * 	403 - Returned if the calling user does not have permission to delete the worklog<br>
	 * 	500 - ClientHandlerException<br>
	 * @throws IOException 
	 * @throws InvalidAccessTokenException 
	 */
	public int deleteWorklog(long worklogId, String jiraKey) throws IOException {
		
		try {
			String resource = "/rest/api/2/issue/" + jiraKey + "/worklog/" + worklogId;
			int resp = AtlassianOAuthClient.makeAuthenticatedRequest(OAuthMessage.GET, GlobalConstants.JIRA_URL + resource, null).status_code;
			if (resp == 200) {
				return AtlassianOAuthClient.makeAuthenticatedRequest(OAuthMessage.DELETE, GlobalConstants.JIRA_URL + resource, null).status_code;
			} else if (resp == 404) {
				return 200;
			} else {
				return resp;
			}
		} catch (Exception e) {
			return 500;
		}
	}

	public void setSign(String sign) {
		user_sign = sign;
	}
	
}
