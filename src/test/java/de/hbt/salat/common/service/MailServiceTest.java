package de.hbt.salat.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.service.MailService.EmailRequest;
import de.hbt.salat.common.service.MailService.MailContact;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

/** A mail with an HTML part carries text and HTML as alternatives and the logo inside (#1505). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class MailServiceTest {

  private final JavaMailSender mailSender = mock(JavaMailSender.class);
  private final MailService service = new MailService(mailSender);

  @Test
  void with_html_sends_text_and_html_as_alternatives_and_embeds_the_logo() throws Exception {
    var parts = send(EmailRequest.builder().htmlMessage("<p>Hallo</p><img src=\"cid:salat-logo\">"));

    assertThat(parts).anyMatch(part -> part.startsWith("multipart/alternative"));
    assertThat(parts).anyMatch(part -> part.startsWith("text/plain"));
    assertThat(parts).anyMatch(part -> part.startsWith("text/html"));
    assertThat(parts).anyMatch(part -> part.startsWith("image/png") && part.contains("<salat-logo>"));
  }

  @Test
  void without_html_sends_the_text_alone() throws Exception {
    var parts = send(EmailRequest.builder());

    assertThat(parts).anyMatch(part -> part.startsWith("text/plain"));
    assertThat(parts).noneMatch(part -> part.startsWith("text/html") || part.startsWith("image/"));
  }

  /** The subject starts with an emoji (#1505): it travels encoded as UTF-8 and arrives unchanged. */
  @Test
  void sends_an_emoji_in_the_subject_encoded_as_utf8() throws Exception {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    service.sendEmail(EmailRequest.builder().subject("🥗 Erinnerung").message("Hallo")
        .from(new MailContact("Salat", "noreply@example.org"))
        .to(new MailContact("Person B", "person.b@example.org"))
        .build());
    var sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(sent.capture());

    assertThat(sent.getValue().getHeader("Subject")[0]).startsWith("=?UTF-8?");
    assertThat(sent.getValue().getSubject()).isEqualTo("🥗 Erinnerung");
  }

  /** The content types of every part of the sent mail, the image with its content id. */
  private List<String> send(EmailRequest.EmailRequestBuilder request) throws Exception {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    service.sendEmail(request.subject("Betreff").message("Hallo")
        .from(new MailContact("Salat", "noreply@example.org"))
        .to(new MailContact("Person B", "person.b@example.org"))
        .build());
    var sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(sent.capture());
    sent.getValue().saveChanges();
    var parts = new ArrayList<String>();
    collect(sent.getValue(), parts);
    return parts;
  }

  private static void collect(Part part, List<String> parts) throws Exception {
    var contentId = part instanceof jakarta.mail.internet.MimePart mime && mime.getContentID() != null
        ? " " + mime.getContentID() : "";
    parts.add(part.getContentType().toLowerCase() + contentId);
    if (part.getContent() instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        collect(multipart.getBodyPart(i), parts);
      }
    }
  }
}
