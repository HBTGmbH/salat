package org.tb.web.util;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;

/**
 * Builds the various emails  
 * 
 * 
 * @author la
 * 
 */

public class SimpleMailFactory {

	/* Email for released report */
	public static SimpleEmail createStatusReportReleasedMail(Statusreport report) throws EmailException {
		StringBuilder subject = new StringBuilder("Statusbericht zum Auftrag ");
		subject.append(report.getCustomerorder().getSignAndDescription());
		subject.append(" freigegeben");
		StringBuilder message = new StringBuilder("Hallo ");
		message.append(report.getRecipient().getFirstname());
		message.append("\n\n");
		message.append("Der Statusbericht zum ");
		message.append(report.getCustomerorder().getSignAndDescription());
		message.append(" wurde freigegeben.");
		message.append("\n\n");
		message.append(report.getSender().getName());
		message.append("\n\n");
		message.append("__________________________");
		message.append("\n\n");
		message.append("(Dies ist eine automatisch erzeugte Email.)");
		
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(GlobalConstants.MAIL_HOST);
		mail.setFrom(report.getSender().getEmailAddress());
		mail.addTo(report.getRecipient().getEmailAddress());
		mail.setSubject(subject.toString());
		mail.setMsg(message.toString());
		return mail;
	}

	/* Email for Salatbuchungen to release */
	public static SimpleEmail createSalatBuchungenToReleaseMail(Employee recipient, Employee sender) throws EmailException {
		String subject = "Freigabe: SALAT freigeben";
		StringBuilder message = new StringBuilder();
		if (GlobalConstants.GENDER_FEMALE.equals(recipient.getGender())) {
			message.append("Liebe ");
		} else {
			message.append("Lieber ");
		}
		message.append(recipient.getFirstname());
		message.append(",\n\n");
		message.append("bitte gib deine SALAT-Buchungen des abgelaufenen Monats frei.");
		message.append("\n\n");
		message.append(sender.getName());
		message.append("\n\n");
		message.append("__________________________");
		message.append("\n\n");
		message.append("(Dies ist eine automatisch erzeugte Email.)");
		
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(GlobalConstants.MAIL_HOST);
		mail.setFrom(sender.getEmailAddress());
		mail.addTo(recipient.getEmailAddress());
		mail.setSubject(subject);
		mail.setMsg(message.toString());
		return mail;
	}

	public static Email createSalatBuchungenToAcceptanceMail(Employee recipient, Employee coworker, Employee sender) throws EmailException {
		String subject = "SALAT: freigegebene Buchungen abnehmen";
		StringBuilder message = new StringBuilder();
		if (GlobalConstants.GENDER_FEMALE.equals(recipient.getGender())) {
			message.append("Liebe ");
		} else {
			message.append("Lieber ");
		}
		message.append(recipient.getFirstname());
		message.append(",\n\n");
		message.append("bitte nimm die SALAT-Buchungen des abgelaufenen Monats von ");
		if (GlobalConstants.GENDER_FEMALE.equals(coworker.getGender())) {
			message.append("Kollegin ");
		} else {
			message.append("Kollege ");
		}
		message.append(coworker.getName());
		message.append(" ab.");
		message.append("\n\n");
		message.append(sender.getName());
		message.append("\n\n");
		message.append("__________________________");
		message.append("\n\n");
		message.append("(Dies ist eine automatisch erzeugte Email.)");
		
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(GlobalConstants.MAIL_HOST);
		mail.setFrom(sender.getEmailAddress());
    	mail.addTo(recipient.getEmailAddress());
		mail.setSubject(subject);
		mail.setMsg(message.toString());
		return mail;
	}

	public static Email createSalatBuchungenReleasedMail(Employee recipient, Employee sender) throws EmailException {
		StringBuilder subject = new StringBuilder("SALAT: Buchungen durch ");
		subject.append(sender.getSign());
		subject.append(" freigegeben");
		StringBuilder message = new StringBuilder();
		if (GlobalConstants.GENDER_FEMALE.equals(recipient.getGender())) {
			message.append("Liebe Personalverantwortliche "); // ehemals Bereichsleiterin
		} else {
			message.append("Lieber Personalverantwortlicher "); // ehemals Bereichsleiter
		}
		message.append(recipient.getFirstname());
		message.append(",\n\n");
		message.append(sender.getName());
		message.append(" hat eben ");
		if (GlobalConstants.GENDER_FEMALE.equals(sender.getGender())) {
			message.append("ihre ");
		} else {
			message.append("seine ");
		}
		message.append("SALAT-Buchungen freigegeben.");
		message.append("\n");
		message.append("Bitte nimm diese ab.");
		message.append("\n\n");
		message.append("__________________________");
		message.append("\n\n");
		message.append("(Dies ist eine automatisch erzeugte Email.)");

		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(GlobalConstants.MAIL_HOST);
		mail.setFrom(sender.getEmailAddress());
		mail.addTo(recipient.getEmailAddress());
		mail.setSubject(subject.toString());
		mail.setMsg(message.toString());
		return mail;
	}

}
