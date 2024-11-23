package org.tb.common.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.springframework.stereotype.Service;
import org.tb.common.SalatProperties;

/**
 * Builds the various emails
 *
 * @author la
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleMailService {

  private final SalatProperties salatProperties;

  public void sendEmail(String subject, String message, MailContact from, MailContact to) {
    try {
      StringBuilder emailMessage = new StringBuilder(message);
      emailMessage.append("\n\n");
      emailMessage.append("__________________________");
      emailMessage.append("\n\n");
      emailMessage.append("(Dies ist eine automatisch erzeugte Email.)");

      SimpleEmail mail = new SimpleEmail();
      mail.setHostName(salatProperties.getMailHost());
      mail.setCharset(org.apache.commons.mail.EmailConstants.UTF_8);
      mail.setFrom(from.getEmail(), from.getName());
      mail.addTo(to.getEmail(), to.getName());
      mail.setSubject(subject);
      mail.setMsg(emailMessage.toString());

      mail.send();
    } catch (EmailException e) {
      log.error("Could not send Email to {}, message: {}", to.getEmail(), message, e);
    }
  }

  @Data
  @RequiredArgsConstructor
  public static class MailContact {

    private final String name;
    private final String email;

  }

}
