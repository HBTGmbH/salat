package org.tb.common;

import lombok.RequiredArgsConstructor;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.springframework.stereotype.Service;
import org.tb.common.configuration.SalatProperties;
import org.tb.employee.Employee;
import org.tb.statusreport.Statusreport;

/**
 * Builds the various emails
 *
 * @author la
 */
@Service
@RequiredArgsConstructor
public class SimpleMailService {

    private final SalatProperties salatProperties;

    public void sendStatusReportReleasedEmail(Statusreport report) throws EmailException {
        createStatusReportReleasedMail(report).send();
    }

    public void sendSalatBuchungenToReleaseMail(Employee recipient, Employee sender) throws EmailException {
        createSalatBuchungenToReleaseMail(recipient, sender).send();
    }

    public void sendSalatBuchungenToAcceptanceMail(Employee recipient, Employee coworker, Employee sender) throws EmailException {
        createSalatBuchungenToAcceptanceMail(recipient, coworker, sender).send();
    }

    public void sendSalatBuchungenReleasedMail(Employee recipient, Employee sender) throws EmailException {
        createSalatBuchungenReleasedMail(recipient, sender).send();
    }

    private SimpleEmail createBasicEmail(String subject, StringBuilder message, Employee sender, Employee recipient) throws EmailException {
        SimpleEmail mail = new SimpleEmail();
        mail.setHostName(salatProperties.getMailHost());
        mail.setCharset(org.apache.commons.mail.EmailConstants.UTF_8);
        mail.setFrom(sender.getEmailAddress(), sender.getName());
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
    private SimpleEmail createStatusReportReleasedMail(Statusreport report) throws EmailException {
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
    private SimpleEmail createSalatBuchungenToReleaseMail(Employee recipient, Employee sender) throws EmailException {
        String subject = "Freigabe: SALAT freigeben";
        StringBuilder message = new StringBuilder();
        if (GlobalConstants.GENDER_FEMALE == recipient.getGender()) {
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

    private Email createSalatBuchungenToAcceptanceMail(Employee recipient, Employee coworker, Employee sender) throws EmailException {
        String subject = "SALAT: freigegebene Buchungen abnehmen";
        StringBuilder message = new StringBuilder();
        if (GlobalConstants.GENDER_FEMALE == recipient.getGender()) {
            message.append("Liebe ");
        } else {
            message.append("Lieber ");
        }
        message.append(recipient.getFirstname());
        message.append(",\n\n");
        message.append("bitte nimm die SALAT-Buchungen des abgelaufenen Monats von ");
        if (GlobalConstants.GENDER_FEMALE == coworker.getGender()) {
            message.append("Kollegin ");
        } else {
            message.append("Kollege ");
        }
        message.append(coworker.getName());
        message.append(" ab.\n\n");
        message.append(sender.getName());

        return createBasicEmail(subject, message, sender, recipient);
    }

    private Email createSalatBuchungenReleasedMail(Employee recipient, Employee sender) throws EmailException {
        String subject = "SALAT: Buchungen durch " + sender.getSign() + " freigegeben";
        StringBuilder message = new StringBuilder();
        if (GlobalConstants.GENDER_FEMALE == recipient.getGender()) {
            message.append("Liebe Personalverantwortliche "); // ehemals Bereichsleiterin
        } else {
            message.append("Lieber Personalverantwortlicher "); // ehemals Bereichsleiter
        }
        message.append(recipient.getFirstname());
        message.append(",\n\n");
        message.append(sender.getName());
        message.append(" hat eben ");
        if (GlobalConstants.GENDER_FEMALE == sender.getGender()) {
            message.append("ihre ");
        } else {
            message.append("seine ");
        }
        message.append("SALAT-Buchungen freigegeben.\n");
        message.append("Bitte nimm diese ab.");

        return createBasicEmail(subject, message, sender, recipient);
    }

}
