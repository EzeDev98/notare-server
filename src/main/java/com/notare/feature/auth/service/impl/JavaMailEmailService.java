package com.notare.feature.auth.service.impl;

import com.notare.feature.auth.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import static com.notare.common.constant.GeneralConstant.SUBJECT;

@RequiredArgsConstructor
@Service
public class JavaMailEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendEmail(String toEmail, String recipient, String otpCode) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(SUBJECT);
        message.setText("Hi " + recipient + ",\n\n"
                + "Your verification code is: " + otpCode + "\n"
                + "This code expires in 10 minutes.\n\n"
                + "If you didn't request this, you can ignore this email.");
        mailSender.send(message);
    }
}
