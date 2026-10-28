package com.xiaoli.yunpicture.infrastructure.manager.email;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;

import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import java.util.concurrent.TimeUnit;

/**
 * 邮件发送与 Redis 验证码管理器
 */
@Slf4j
@Component
public class EmailManager {
    // 发送邮件的 Bean
    @Resource
    private JavaMailSender mailSender;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    // 验证码 Redis 前缀
    private static final String CODE_KEY_PREFIX = "user:code:";
    // 防刷限流 Redis 前缀（60秒冷却）
    private static final String COOLDOWN_KEY_PREFIX = "user:code:cooldown:";

    /**
     * 发送邮箱验证码并存入 Redis
     *
     * @param toEmail 目标邮箱
     * @param biz     业务场景 (register / reset_password)
     */
    public void sendVerificationCode(String toEmail, String biz) {
        if (StrUtil.isBlank(toEmail) || StrUtil.isBlank(biz)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (StrUtil.isBlank(fromEmail)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        // =========================================================================
        // 1. 【核心】：在最前面就用 setIfAbsent 抢占 60 秒冷却位（一步完成检查 + 占位）
        // 如果抢到了（true），说明 60 秒内没人发过，放行；
        // 如果没抢到（false），说明有人正在发或 60 秒内发过，直接当场拦截！
        // =========================================================================
        String cooldownKey = COOLDOWN_KEY_PREFIX + biz + ":" + toEmail;

        Boolean acquireSuccess = stringRedisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", 60, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquireSuccess) || acquireSuccess == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码发送过于频繁，请稍后再试");
        }

        // 2. 生成 6 位随机数字验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 构建邮件内容并发送
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail, "绘灵云图片协作平台");
            helper.setTo(toEmail);

            String title = "register".equals(biz) ? "注册验证码" : "找回密码验证码";
            helper.setSubject("【绘灵云图片协作平台】" + title);

            String htmlContent = "<div style='font-family: Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 24px; border: 1px solid #eee; border-radius: 8px;'>"
                    + "<h2 style='color: #1890ff; margin-bottom: 16px;'>绘灵云图片协作平台</h2>"
                    + "<p style='font-size: 15px; color: #333;'>您好！您正在进行<strong>" + title + "</strong>操作，您的验证码为：</p>"
                    + "<div style='background: #f4f6f9; padding: 16px; text-align: center; border-radius: 6px; margin: 20px 0;'>"
                    + "<span style='font-size: 30px; font-weight: bold; letter-spacing: 6px; color: #1890ff;'>" + code + "</span>"
                    + "</div>"
                    + "<p style='font-size: 13px; color: #888;'>验证码有效期为 <strong>5 分钟</strong>。若非本人操作，请忽略此邮件。</p>"
                    + "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'/>"
                    + "<p style='font-size: 12px; color: #bbb; text-align: center;'>此邮件由系统自动发送，请勿直接回复</p>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
            log.info("邮件验证码已成功发送至: {}, 场景: {}", toEmail, biz);
        } catch (Exception e) {
            log.error("邮件发送异常: ", e);
            // 异常兜底：如果邮件发送失败了，把刚抢到的冷却锁删掉，允许用户重新尝试
            stringRedisTemplate.delete(cooldownKey);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "邮件发送失败，请稍后重试");
        }

        // 4. 将验证码保存至 Redis，5 分钟有效
        String codeKey = CODE_KEY_PREFIX + biz + ":" + toEmail;
        stringRedisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);

        // 第 5 步彻底删掉！因为第 1 步已经原子性地设置了 60 秒冷却，这里无需重复设置！
    }

    /**
     * 校验验证码是否正确
     *
     * @param toEmail 邮箱
     * @param biz     业务场景
     * @param code    用户输入的验证码
     */
    public void verifyCode(String toEmail, String biz, String code) {
        if (StrUtil.hasBlank(toEmail, biz, code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String codeKey = CODE_KEY_PREFIX + biz + ":" + toEmail;
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (StrUtil.isBlank(cachedCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (!cachedCode.equals(code.trim())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误");
        }
    }

    /**
     * 消费/清除已验证的验证码（防止二次利用）
     */
    public void deleteCode(String toEmail, String biz) {
        String codeKey = CODE_KEY_PREFIX + biz + ":" + toEmail;
        stringRedisTemplate.delete(codeKey);
    }

    /**
     * 发送图片文件清理失败告警。
     *
     * @param toEmail 超级管理员邮箱
     * @param taskId  首次触发告警的清理任务 id
     */
    public void sendPictureCleanupFailureAlert(String toEmail, Long taskId) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail, "绘灵云图片协作平台");
            helper.setTo(toEmail);
            helper.setSubject("【绘灵云告警】图片文件清理任务执行失败");

            String htmlContent = "<div style='font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; padding: 24px; border: 1px solid #eee; border-radius: 8px;'>"
                    + "<h2 style='color: #cf1322; margin-bottom: 16px;'>图片文件清理失败</h2>"
                    + "<p style='font-size: 15px; color: #333;'>系统检测到图片文件清理任务最终失败。</p>"
                    + "<p style='font-size: 15px; color: #333;'>首次触发任务 ID：<strong>" + taskId + "</strong></p>"
                    + "<p style='font-size: 15px; color: #333;'>请登录超级管理员后台，在“图片管理 → 文件清理异常”中查看并人工处理。</p>"
                    + "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'/>"
                    + "<p style='font-size: 12px; color: #bbb; text-align: center;'>此邮件由系统自动发送，请勿直接回复</p>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
            log.info("图片清理失败告警邮件已发送至: {}，taskId: {}", toEmail, taskId);
        } catch (Exception exception) {
            log.error("图片清理失败告警邮件发送异常，taskId: {}", taskId, exception);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片清理失败告警邮件发送失败");
        }
    }
}
