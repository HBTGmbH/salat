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

	private static final String HOST = "MSG01";
	private static final String FROM = "NoReply@hbt.de";

	/* Email for released report */
	public static SimpleEmail createStatusReportReleasedMail(
			Statusreport report) throws EmailException {

		String subject = "Statusbericht zum Auftrag "
				+ report.getCustomerorder().getSign() + "-"
				+ report.getCustomerorder().getDescription() + " freigegeben";
		String message = "Hallo " + report.getRecipient().getFirstname() + ","
				+ "\n" + "\n";
		message += "Der Statusbericht zum "
				+ report.getCustomerorder().getSign() + "-"
				+ report.getCustomerorder().getDescription();
		message += " wurde freigegeben." + "\n" + "\n";
		message += report.getSender().getName() + "\n" + "\n";
		
		message +="__________________________"+"\n"+"\n";
		message +="(Dies ist eine automatisch erzeugte Email. Der technische Absender kann keine Antwort empfangen.)";

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
	public static SimpleEmail createSalatBuchungenToReleaseMail(
			Employee recipient, Employee from) throws EmailException {
		String subject = "Freigabe: SALAT freigeben";
		String title;

		if (recipient.getGender() == GlobalConstants.GENDER_FEMALE) {
			title = "Liebe ";
		} else
			title = "Lieber ";

		String firstname = recipient.getFirstname();

		String message = title + firstname + "," + "\n" + "\n";
		message += "bitte gib deine SALAT-Buchungen des abgelaufenen Monats frei."
				+ "\n" + "\n";
		message += from.getName()+ "\n" + "\n";
		message +="__________________________"+"\n"+"\n";
		message +="(Dies ist eine automatisch erzeugte Email.)";
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(HOST);
		mail.setFrom(from.getSign()+"@hbt.de");
		mail.addTo(recipient.getSign() +"@hbt.de");
		mail.setSubject(subject);
		mail.setMsg(message);
		return mail;
	}

	public static Email createSalatBuchungenToAcceptanceMail(
			Employee recipient, Employee contEmployee, Employee from) throws EmailException {
		String subject = "SALAT: freigegebene Buchungen abnehmen";
		String title;
		

		if (recipient.getGender() == GlobalConstants.GENDER_FEMALE) {
			title = "Liebe Bereichsleiterin ";
		} else
			title = "Lieber Bereichsleiter ";

		String firstname = recipient.getFirstname();

		String message = title + firstname + "," + "\n" + "\n";
		message += "bitte nimm die SALAT-Buchungen des abgelaufenen Monats von Kollege "+contEmployee.getName()+" ab."
				+ "\n" + "\n";
		message += from.getName()+"\n"+"\n";
		message +="__________________________"+"\n"+"\n";
		message +="(Dies ist eine automatisch erzeugte Email.)";
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(HOST);
		mail.setFrom(from.getSign()+"@hbt.de");
    	mail.addTo(recipient.getSign()+"@hbt.de");
		mail.setSubject(subject);
		mail.setMsg(message);
		return mail;
	}

	public static Email createSalatBuchungenReleasedMail(Employee recipient,
			Employee from) throws EmailException {
		String subject = "SALAT: Buchungen durch " + from.getSign() + " freigegeben";
		String title;
		

		if (recipient.getGender() == GlobalConstants.GENDER_FEMALE) {
			title = "Liebe Bereichsleiterin ";
		} else
			title = "Lieber Bereichsleiter ";

		String firstname = recipient.getFirstname();

		String message = title + firstname + "," + "\n" + "\n";
		message += from.getName()+" hat eben die SALAT-Buchungen freigegeben."+"\n";
		message +=	"Bitte nimm diese ab. "+"\n" + "\n";
		message +="__________________________"+"\n"+"\n";
		message +="(Dies ist eine automatisch erzeugte Email. Der technische Absender kann keine Antwort empfangen.)";
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(HOST);
		mail.setFrom(FROM);
		mail.addTo(recipient.getSign() +"@hbt.de");
		mail.setSubject(subject);
		mail.setMsg(message);
		return mail;
	}

}
