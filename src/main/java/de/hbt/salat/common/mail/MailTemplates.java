package de.hbt.salat.common.mail;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders the HTML part of a mail from a template under {@code templates/mail/} (#1505), which frames
 * its content with the common layout ({@code mail/layout}). A mail is also rendered outside a request
 * — from a job, after the commit —, so the templates know no request: every link comes in as an
 * absolute address, never through {@code @{…}}.
 */
@Component
@RequiredArgsConstructor
public class MailTemplates {

  private final ITemplateEngine templateEngine;

  public String render(String template, Locale locale, Map<String, Object> variables) {
    var all = new HashMap<>(variables);
    all.put("colors", MailColors.SALAT);
    return templateEngine.process(template, new Context(locale, all));
  }
}
