package org.tb.web.util;

import org.apache.commons.mail.EmailException;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;

/**
 * Class sends the emails from the {@link SimpleMailFactory}
 *
 * @author la
 */
public class MailSender {

    public static void sendStatusReportReleasedEmail(Statusreport report) throws EmailException {
        SimpleMailFactory.createStatusReportReleasedMail(report).send();
    }

    public static void sendSalatBuchungenToReleaseMail(Employee recipient, Employee sender) throws EmailException {
        SimpleMailFactory.createSalatBuchungenToReleaseMail(recipient, sender).send();
    }

    public static void sendSalatBuchungenToAcceptanceMail(Employee recipient, Employee coworker, Employee sender) throws EmailException {
        SimpleMailFactory.createSalatBuchungenToAcceptanceMail(recipient, coworker, sender).send();
    }

    public static void sendSalatBuchungenReleasedMail(Employee recipient, Employee sender) throws EmailException {
        SimpleMailFactory.createSalatBuchungenReleasedMail(recipient, sender).send();
    }

}
