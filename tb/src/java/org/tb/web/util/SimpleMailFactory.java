package org.tb.web.util;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;

/**
 * @author la
 * 
 */

public class SimpleMailFactory {

	private static final String HOST = "MSG01";
	private static final String FROM = "SALAT@hbt.de";

	/* Email for released report */
	public static SimpleEmail createStatusReportReleasedEmail(
			Statusreport report) throws EmailException {

		String subject = "Statusbericht zum Auftrag "
				+ report.getCustomerorder().getSign() + "-"
				+ report.getCustomerorder().getDescription() + " freigegeben";
		String message = "Hallo " + report.getRecipient().getFirstname() + ","
				+ "\n" + "\n";
		message += "Der Statusbericht zum "
				+ report.getCustomerorder().getSign() + "-"
				+ report.getCustomerorder().getDescription();
		message += " wurde freigegeben" + "\n" + "\n";
		message += report.getSender().getName();

		// MitarbeiterKuerzel + extension for Mailadresse
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(HOST);
		mail.setFrom(FROM);
		mail.addTo(report.getRecipient().getLoginname() + "@hbt.de");
		mail.setSubject(subject);
		mail.setMsg(message);
		return mail;
	}

	/* Email for Salatbuchungen to release */
	public static SimpleEmail createSalatBuchungenToReleaseEmail(
			Employee recipient, String from) throws EmailException {
		String subject = "Freigabe: SALAT freigeben";
		String title;

		if (recipient.getGender() == GlobalConstants.GENDER_FEMALE) {
			title = "Liebe ";
		} else
			title = "Lieber ";

		String firstname = recipient.getFirstname();

		String message = title + firstname + "," + "\n" + "\n";
		message += "bitte gib deine SALAT-Buchungen des abgelaufenen Monats frei"
				+ "\n" + "\n";
		message += from;
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(HOST);
		mail.setFrom(FROM);
		mail.addTo(recipient.getSign() + "@hbt.de");
		mail.setSubject(subject);
		mail.setMsg(message);
		return mail;
	}

}
