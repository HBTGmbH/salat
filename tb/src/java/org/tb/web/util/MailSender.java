/**
 * 
 */
package org.tb.web.util;

import javax.security.sasl.SaslException;

import org.apache.commons.mail.EmailException;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;

/**
 * Class sends the emails from the {@link SimpleMailFactory}
 * 
 * @author la
 * 
 */
public class MailSender {

	public static void sendStatusReportReleasedEmail(Statusreport report)
			throws EmailException {

		SimpleMailFactory.createStatusReportReleasedMail(report).send();

	}

	public static void sendSalatBuchungenToReleaseMail(Employee recipient, Employee from)
			throws EmailException {

		SimpleMailFactory.createSalatBuchungenToReleaseMail(recipient, from).send();

	}

	public static void sendSalatBuchungenToAcceptanceMail(Employee recipient,
			Employee contEmployee, Employee from) throws EmailException {
		SimpleMailFactory.createSalatBuchungenToAcceptanceMail(recipient, contEmployee,from).send();
		
	}

	public static void sendSalatBuchungenReleasedMail(Employee recipient,
			Employee from) throws EmailException {
		SimpleMailFactory.createSalatBuchungenReleasedMail(recipient, from).send();
		
	}

}
