package com.notare.feature.auth.service.impl;

import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.resource.Emailv31;
import com.notare.common.exception.EmailDeliveryException;
import com.notare.feature.auth.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.notare.common.constant.GeneralConstant.SUBJECT;

@Slf4j
@Service
public class MailjetEmailService implements EmailService {

    private final MailjetClient mailjetClient;
    private final String fromEmail;
    private final String fromName;

    public MailjetEmailService(
            @Value("${app.mailjet.api-key}") String apiKey,
            @Value("${app.mailjet.secret-key}") String secretKey,
            @Value("${app.mailjet.from-email}") String fromEmail,
            @Value("${app.mailjet.from-name}") String fromName
    ) {
        ClientOptions options = ClientOptions.builder()
                .apiKey(apiKey)
                .apiSecretKey(secretKey)
                .build();

        this.mailjetClient = new MailjetClient(options);
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void sendEmail(String toEmail, String recipient, String otpCode) {

        JSONObject message = new JSONObject()
                .put(Emailv31.Message.FROM, new JSONObject()
                        .put("Email", fromEmail)
                        .put("Name", fromName))
                .put(Emailv31.Message.TO, new JSONArray()
                        .put(new JSONObject()
                                .put("Email", toEmail)
                                .put("Name", recipient)))
                .put(Emailv31.Message.SUBJECT, SUBJECT)
                .put(Emailv31.Message.TEXTPART, buildBody(recipient, otpCode));

        MailjetRequest request = new MailjetRequest(Emailv31.resource).property(Emailv31.MESSAGES, new JSONArray().put(message));

        try {
            MailjetResponse response = mailjetClient.post(request);

            if (response.getStatus() >= 400) {
                log.error("Mailjet rejected email to {}: status={} body={}", toEmail, response.getStatus(), response.getData());
                throw new EmailDeliveryException("Failed to send verification email. Please try again shortly.");
            }

        } catch (MailjetException ex) {
            log.error("Mailjet request failed for {}", toEmail, ex);
            throw new EmailDeliveryException("Failed to send verification email. Please try again shortly.");
        }
    }

    private String buildBody(String recipient, String otpCode) {
        return "Hi " + recipient + ",\n\n"
                + "Your verification code is: " + otpCode + "\n"
                + "This code expires in 10 minutes.\n\n"
                + "If you didn't request this, you can ignore this email.";
    }
}