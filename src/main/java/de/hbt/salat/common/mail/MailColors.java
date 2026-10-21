package de.hbt.salat.common.mail;

/**
 * The colours of every mail Salat sends (#1505), the one place they are written as fixed values.
 * Mail programs know neither CSS variables nor {@code oklch()}, in which Tabler defines its tokens, so
 * the mail cannot follow the rule that every colour derives from a {@code --tblr-*} token
 * ({@code docs/guides/ui.md}, Mails). Each value is the token named beside it, converted to hex; each
 * text colour has at least 4.5:1 against the background it stands on.
 *
 * @param page        {@code --tblr-gray-50}, the background around the mail
 * @param header      {@code --tblr-dark}, the bar with the logo, as the sidebar
 * @param headerText  {@code --tblr-light} on the bar (14.5:1)
 * @param card        white, the card with the content
 * @param border      {@code --tblr-gray-200}
 * @param text        {@code --tblr-gray-800}, the body text (15.1:1 on the card)
 * @param muted       {@code --tblr-gray-500}, secondary text (4.7:1 on the card, 4.5:1 on the page)
 * @param primary     {@code --tblr-primary}, the button and links (5.0:1 on the card)
 * @param primaryText white on the button (5.0:1)
 */
public record MailColors(String page, String header, String headerText, String card, String border, String text,
                         String muted, String primary, String primaryText) {

  public static final MailColors SALAT = new MailColors("#fafafa", "#262626", "#fafafa", "#ffffff", "#e5e5e5",
      "#262626", "#737373", "#066fd1", "#ffffff");
}
