/**
 * 
 */
package org.tb.web.util;

import org.apache.commons.mail.EmailException;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;

/**
 * @author la
 * 
 */
public class MailSender {

	public static void sendStatusReportReleasedEmail(Statusreport report)
			throws EmailException {

		SimpleMailFactory.createStatusReportReleasedEmail(report).send();

	}

	public static void sendSalatBuchungenToReleaseMail(Employee recipient, String from)
			throws EmailException {

		SimpleMailFactory.createSalatBuchungenToReleaseEmail(recipient, from).send();

	}

}
