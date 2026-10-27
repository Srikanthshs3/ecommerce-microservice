package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String username;

    public void sendOtpEmail(String toEmail, String otp){
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username);
            message.setTo(toEmail);
            message.setSubject("Verify Your Registration - OTP");
            message.setText("Welcome! Your one-time password for registration is: " + otp +
                    "\n\nThis code will expire in 5 minutes.");

            mailSender.send(message);
            log.info("OTP email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}. Error: {}", toEmail, e.getMessage());
        }
    }

    public void sendWelcomeEmail(String toEmail){
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username);
            message.setTo(toEmail);
            message.setSubject("Welcome to Our Service!");
            message.setText("Congratulations! Your registration is successful. Welcome aboard!");

            mailSender.send(message);
            log.info("Welcome email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}. Error: {}", toEmail, e.getMessage());
        }
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username);
            message.setTo(toEmail);
            message.setSubject("Password Reset Request");
            message.setText("You requested a password reset. Click the link below to choose a new password:\n" + resetLink +
                    "\n\nThe link works for 15 minutes and can be used once." +
                    "\n\nIf you did not request this, please ignore this email. Your password stays unchanged.");

            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}. Error: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Sends an HTML e-mail with a plain-text fallback. Unlike the older methods this one THROWS when sending fails, so
     * the Kafka listener does not acknowledge the event and the error handler retries it.
     */
    public void sendHtmlEmail(String toEmail, String subject, String html, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(username);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
            log.info("E-mail '{}' sent to: {}", subject, toEmail);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build the e-mail '" + subject + "'", e);
        }
    }
}
