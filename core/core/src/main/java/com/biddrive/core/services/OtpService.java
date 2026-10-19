package com.biddrive.core.services;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final long OTP_TTL_SECONDS = 300;     // 5 minutes
    private static final long COOLDOWN_SECONDS = 60;     // 60 seconds cooldown for resend
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@biddrive.com}")
    private String fromEmail;

    public OtpService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generates a 6-digit OTP, saves in Redis with 5-minute TTL, and dispatches HTML email.
     * Returns the generated OTP so local development / BlueStacks can auto-fill.
     */
    public String generateAndSendOtp(String email, String name) {
        String cleanEmail = email.trim().toLowerCase();
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

        // Store OTP in Redis
        String redisKey = "otp:email:" + cleanEmail;
        redisTemplate.opsForValue().set(redisKey, otp, OTP_TTL_SECONDS, TimeUnit.SECONDS);

        // Reset failed attempts
        redisTemplate.delete("otp:attempts:" + cleanEmail);

        // Set 60-second resend cooldown
        redisTemplate.opsForValue().set("otp:cooldown:" + cleanEmail, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);

        log.info("🔐 [OTP Service] Generated 6-digit OTP for {}: {}", cleanEmail, otp);

        // Send email asynchronously / gracefully
        sendOtpEmail(cleanEmail, name, otp);

        return otp;
    }

    /**
     * Resends OTP respecting 60-second rate-limit cooldown.
     */
    public String resendOtp(String email, String name) {
        String cleanEmail = email.trim().toLowerCase();
        String cooldownKey = "otp:cooldown:" + cleanEmail;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            Long expire = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            throw new RuntimeException("Please wait " + (expire != null ? expire : 60) + " seconds before requesting a new code.");
        }

        return generateAndSendOtp(cleanEmail, name);
    }

    /**
     * Validates input OTP against Redis with anti-brute-force rate limiting.
     */
    public boolean validateOtp(String email, String inputOtp) {
        if (inputOtp == null || inputOtp.isBlank()) {
            return false;
        }

        String cleanEmail = email.trim().toLowerCase();
        String cleanOtp = inputOtp.trim();
        String redisKey = "otp:email:" + cleanEmail;
        String attemptsKey = "otp:attempts:" + cleanEmail;

        String storedOtp = redisTemplate.opsForValue().get(redisKey);
        if (storedOtp == null) {
            log.warn("⚠️ [OTP Service] OTP for {} is expired or not found.", cleanEmail);
            throw new RuntimeException("Verification code has expired. Please request a new code.");
        }

        if (storedOtp.equals(cleanOtp)) {
            // Success: Clean up Redis keys
            redisTemplate.delete(redisKey);
            redisTemplate.delete(attemptsKey);
            redisTemplate.delete("otp:cooldown:" + cleanEmail);
            log.info("✅ [OTP Service] OTP successfully verified for {}", cleanEmail);
            return true;
        }

        // Invalid code: Track failed attempts
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts >= MAX_FAILED_ATTEMPTS) {
            redisTemplate.delete(redisKey);
            redisTemplate.delete(attemptsKey);
            log.warn("❌ [OTP Service] Exceeded max failed attempts for {}. Code revoked.", cleanEmail);
            throw new RuntimeException("Too many incorrect attempts. This verification code has been revoked. Please request a new one.");
        }

        int remaining = MAX_FAILED_ATTEMPTS - (attempts != null ? attempts.intValue() : 1);
        throw new RuntimeException("Incorrect verification code. " + remaining + " attempt(s) remaining.");
    }

    private void sendOtpEmail(String toEmail, String name, String otp) {
        if (mailSender == null) {
            log.info("📧 [Email Service] JavaMailSender not configured. Email preview logged for {}: OTP={}", toEmail, otp);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "BidDrive Security");
            helper.setTo(toEmail);
            helper.setSubject("BidDrive Verification Code: " + otp);

            String displayName = (name != null && !name.isBlank()) ? name : "Rider";
            String htmlContent = buildHtmlEmailTemplate(displayName, otp);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("📧 [Email Service] Successfully sent verification email to {}", toEmail);
        } catch (Exception e) {
            log.warn("⚠️ [Email Service] Failed to send email via SMTP (Falling back to console): {}", e.getMessage());
        }
    }

    private String buildHtmlEmailTemplate(String name, String otp) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:24px;background-color:#F5F5F5;font-family:Arial,sans-serif;color:#1A1A1A;'>"
                + "  <div style='max-width:520px;margin:0 auto;background:#FFFFFF;border-radius:16px;padding:32px;box-shadow:0 4px 12px rgba(0,0,0,0.08);'>"
                + "    <div style='display:flex;align-items:center;margin-bottom:24px;'>"
                + "      <div style='background-color:#FFC000;width:40px;height:40px;border-radius:50%;text-align:center;line-height:40px;font-size:22px;'>🚖</div>"
                + "      <h2 style='margin:0 0 0 12px;color:#1A1A1A;font-size:20px;font-weight:800;'>BidDrive Verification</h2>"
                + "    </div>"
                + "    <p style='font-size:15px;line-height:1.5;color:#333333;'>Hello <b>" + name + "</b>,</p>"
                + "    <p style='font-size:14px;line-height:1.5;color:#666666;'>Welcome to BidDrive! Please use the following 6-digit verification code to activate your account:</p>"
                + "    <div style='margin:28px 0;text-align:center;background:#FFF9E6;border:2px dashed #FFC000;border-radius:12px;padding:18px;'>"
                + "      <span style='font-size:36px;font-weight:900;letter-spacing:10px;color:#1A1A1A;'>" + otp + "</span>"
                + "    </div>"
                + "    <p style='font-size:13px;color:#888888;margin-bottom:24px;'>⏳ This code is valid for <b>5 minutes</b>. For your security, never share this code with anyone.</p>"
                + "    <hr style='border:none;border-top:1px solid #EEEEEE;margin:24px 0;'/>"
                + "    <p style='font-size:11px;color:#AAAAAA;margin:0;'>If you did not request this verification, you can safely ignore this email.</p>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }
}
