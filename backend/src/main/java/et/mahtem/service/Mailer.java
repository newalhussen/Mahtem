package et.mahtem.service;

/**
 * Outbound email. The default implementation only logs, so the app runs without an SMTP server;
 * provide another {@code Mailer} bean (SMTP, SES, Mailgun…) to deliver for real.
 */
public interface Mailer {
    void send(String to, String subject, String body);
}
