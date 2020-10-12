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

	private static SimpleEmail createBasicEmail(String subject, StringBuilder message, Employee sender, Employee recipient) throws EmailException {
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(GlobalConstants.MAIL_HOST);
		mail.setCharset(org.apache.commons.mail.EmailConstants.UTF_8);
		mail.setFrom(sender.getEmailAddress(), sender.getName());
//		mail.setFrom(GlobalConstants.MAIL_NOREPLY_ADDRESS, "HBT-SALAT");
//		mail.addReplyTo(sender.getEmailAddress(), sender.getName());
    	mail.addTo(recipient.getEmailAddress(), recipient.getName());
    	
    	message.append("\n\n");
    	message.append("__________________________");
    	message.append("\n\n");
    	message.append("(Dies ist eine automatisch erzeugte Email.)");

		mail.setSubject(subject);
		mail.setMsg(message.toString());
		return mail;
	}
	
	/* Email for released report */
	public static SimpleEmail createStatusReportReleasedMail(Statusreport report) throws EmailException {
		String subject = "Statusbericht zum Auftrag " + report.getCustomerorder().getSignAndDescription() + " freigegeben";
		StringBuilder message = new StringBuilder("Hallo ");
		message.append(report.getRecipient().getFirstname());
		message.append("\n\n");
		message.append("Der Statusbericht zum ");
		message.append(report.getCustomerorder().getSignAndDescription());
		message.append(" wurde freigegeben.\n\n");
		message.append(report.getSender().getName());
		
		return createBasicEmail(subject, message, report.getSender(), report.getRecipient());
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
		message.append("bitte gib deine SALAT-Buchungen des abgelaufenen Monats frei.\n\n");
		message.append(sender.getName());
		
		return createBasicEmail(subject, message, sender, recipient);
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
		message.append(" ab.\n\n");
		message.append(sender.getName());
		
		return createBasicEmail(subject, message, sender, recipient);
	}

	public static Email createSalatBuchungenReleasedMail(Employee recipient, Employee sender) throws EmailException {
		String subject = "SALAT: Buchungen durch " + sender.getSign() + " freigegeben";
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
		message.append("SALAT-Buchungen freigegeben.\n");
		message.append("Bitte nimm diese ab.");

		return createBasicEmail(subject, message, sender, recipient);
	}

}
