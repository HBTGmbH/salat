package org.tb.helper;

import static net.oauth.OAuth.OAUTH_VERIFIER;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthServiceProvider;
import net.oauth.client.OAuthClient;
import net.oauth.client.httpclient4.HttpClient4;
import net.oauth.http.HttpMessage;
import net.oauth.signature.RSA_SHA1;

import org.codehaus.jettison.json.JSONObject;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;

import com.google.common.collect.ImmutableList;

/**
 * Helper class for performing the "OAuth dance" and making requests to Jira OAuth
 * 
 * @author mgo
 *
 */
public class AtlassianOAuthClient {
	
	protected static final String SERVLET_BASE_URL = "/plugins/servlet";

	private static String consumerKey = GlobalConstants.JIRA_CONSUMER_KEY;
	private static String privateKey = GlobalConstants.JIRA_CONSUMER_PRIVATE_KEY;
	private static String baseUrl = GlobalConstants.JIRA_URL;
	private static OAuthAccessor accessor;
	private static TokenSecretVerifierHolder tokenSecretVerifier;


	public static TokenSecretVerifierHolder getRequestToken(String callback) {
		try {
			OAuthAccessor accessor = getAccessor(callback);
			OAuthClient oAuthClient = new OAuthClient(new HttpClient4());
			List<OAuth.Parameter> callBack;
			if (callback == null || "".equals(callback)) {
				callBack = Collections.<OAuth.Parameter> emptyList();
			} else {
				callBack = ImmutableList.of(new OAuth.Parameter(OAuth.OAUTH_CALLBACK, callback));
			}

			OAuthMessage message = oAuthClient.getRequestTokenResponse(accessor, OAuthMessage.POST, callBack);

			tokenSecretVerifier = new TokenSecretVerifierHolder();
			tokenSecretVerifier.token = accessor.requestToken;
			tokenSecretVerifier.secret = accessor.tokenSecret;
			tokenSecretVerifier.verifier = message.getParameter(OAUTH_VERIFIER);

			return tokenSecretVerifier;
		} catch (Exception e) {
			throw new RuntimeException("Failed to obtain request token", e);
		}
	}

	public static String swapRequestTokenForAccessToken(String oauthVerifier, EmployeeDAO employeeDAO, Employee employee) {
		try {
			OAuthAccessor accessor = getAccessor(null);
			OAuthClient client = new OAuthClient(new HttpClient4());
			accessor.requestToken = tokenSecretVerifier.token; // requestToken;
			accessor.tokenSecret = tokenSecretVerifier.secret; // tokenSecret;

			OAuthMessage message = client.getAccessToken(accessor, OAuthMessage.POST,
					ImmutableList.of(new OAuth.Parameter(OAuth.OAUTH_VERIFIER, oauthVerifier)));
			accessor.accessToken = message.getToken();
			
			employee.setJira_oauthtoken(message.getToken());
			employeeDAO.save(employee, employee);
			
			return accessor.accessToken;
		} catch (Exception e) {
//			if (e.getMessage() == "token_rejected") getRequestToken(callback);
			throw new RuntimeException("Failed to swap request token with access token", e);
		}
	}
	

	public static void getRequestTokenAndSetRedirectToJira(HttpServletResponse response, String callbackURL) throws IOException {
		
		TokenSecretVerifierHolder requestToken = getRequestToken(callbackURL);
		String authorizeUrl = getAuthorizeUrlForToken(requestToken.token);
		
		response.sendRedirect(authorizeUrl);
	}
	
	public static boolean isValidAccessToken(String jiraAccessToken) {
		//TODO: do something!
//		makeAuthenticatedRequest(OAuthMessage.)
		return true;
	}

	
	public static HttpResponse makeAuthenticatedRequest(String httpMethod, String url, JSONObject jsonBody) 
			throws URISyntaxException, IOException {
		
		OAuthClient client = new OAuthClient(new HttpClient4());
		HttpResponse httpResponse = new HttpResponse();
        OAuthMessage response = null;
        
        try {        	
        	
        	if (accessor == null) accessor = getAccessor(url);
        	if (accessor.accessToken == null) throw new RuntimeException("Access Token should not be null");
        	
        	if (httpMethod == OAuthMessage.GET) {
				response = client.invoke(accessor, url, Collections.<Map.Entry<?, ?>>emptySet());
			} else { // PUT, POST oder DELETE        	
				OAuthMessage request = null;
				
				if (jsonBody == null) {
					request = accessor.newRequestMessage(httpMethod, url, Collections.<Map.Entry<?,?>>emptySet());
				} else {
					InputStream bodyIS = new ByteArrayInputStream(jsonBody.toString().getBytes());
					request = accessor.newRequestMessage(httpMethod, url, Collections.<Map.Entry<?,?>>emptySet(), bodyIS);
					request.getHeaders().add(new OAuth.Parameter(HttpMessage.CONTENT_TYPE, "application/json"));
				}
				
				Object accepted = accessor.consumer.getProperty(OAuthConsumer.ACCEPT_ENCODING);
				if (accepted != null) {
					request.getHeaders().add(new OAuth.Parameter(HttpMessage.ACCEPT_ENCODING, accepted.toString()));
				}
			
				Object ps = accessor.consumer.getProperty(OAuthClient.PARAMETER_STYLE);
				net.oauth.ParameterStyle style = (ps == null) ? net.oauth.ParameterStyle.BODY
						: Enum.valueOf(net.oauth.ParameterStyle.class, ps.toString());

				response = client.invoke(request, style);
			} 	
	        
			httpResponse.status_code = 200;
			httpResponse.message_body = response.readBodyAsString();
        } catch (OAuthException e) {
        	httpResponse.status_code = ((OAuthProblemException) e).getHttpStatusCode();
        } catch (NullPointerException e) {
        	httpResponse.message_body = new String();
		}
        return httpResponse;
    }
	
	
	private static final OAuthAccessor getAccessor(String callback) {
		if (accessor == null) {
			OAuthServiceProvider serviceProvider = new OAuthServiceProvider(
					getRequestTokenUrl(), 
					getAuthorizeUrl(),
					getAccessTokenUrl());
			OAuthConsumer consumer = new OAuthConsumer(callback, consumerKey, null,	serviceProvider);
			consumer.setProperty(RSA_SHA1.PRIVATE_KEY, privateKey);
			consumer.setProperty(OAuth.OAUTH_SIGNATURE_METHOD, OAuth.RSA_SHA1);
			accessor = new OAuthAccessor(consumer);
		}
		return accessor;
	}

	private static String getAccessTokenUrl() {
		return baseUrl + SERVLET_BASE_URL + "/oauth/access-token";
	}

	private static String getRequestTokenUrl() {
		return baseUrl + SERVLET_BASE_URL + "/oauth/request-token";
	}

	private static String getAuthorizeUrlForToken(String token) {
		return getAuthorizeUrl() + "?oauth_token=" + token;
	}

	private static String getAuthorizeUrl() {
		return baseUrl + SERVLET_BASE_URL + "/oauth/authorize";
	}

	public static void setAccessToken(String accessToken) {
		getAccessor(null).accessToken = accessToken;
	}

}
