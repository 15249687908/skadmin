package com.dxj.tool.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.dxj.tool.domain.VerificationCode;
import com.dxj.tool.domain.vo.EmailVo;
import com.dxj.common.exception.BadRequestException;
import com.dxj.tool.repository.VerificationCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.*;

/**
 * @author dxj
 * @date 2019-05-26
 */
@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true, rollbackFor = Exception.class)
public class VerificationCodeService {

    private final VerificationCodeRepository verificationCodeRepository;

    @Value("${code.expiration}")
    private Integer expiration;

    @Autowired
    public VerificationCodeService(VerificationCodeRepository verificationCodeRepository) {
        this.verificationCodeRepository = verificationCodeRepository;
    }

    /**
     * 发送邮件验证码
     *
     * @param code
     */
    @Transactional(rollbackFor = Exception.class)
    public EmailVo sendEmail(VerificationCode code) {
        EmailVo emailVo;
        String content;
        VerificationCode verificationCode = verificationCodeRepository.findByScenesAndTypeAndValueAndStatusIsTrue(code.getScenes(), code.getType(), code.getValue());
        // 如果不存在有效的验证码，就创建一个新的
        TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig("template", TemplateConfig.ResourceMode.CLASSPATH));
        Template template = engine.getTemplate("email/email.ftl");
        if (verificationCode == null) {
            code.setCode(RandomUtil.randomNumbers(6));
            content = template.render(Dict.create().set("code", code.getCode()));
            emailVo = new EmailVo(Collections.singletonList(code.getValue()), "skadmin管理系统", content);
            timedDestruction(verificationCodeRepository.save(code));
            // 存在就再次发送原来的验证码
        } else {
            content = template.render(Dict.create().set("code", verificationCode.getCode()));
            emailVo = new EmailVo(Collections.singletonList(verificationCode.getValue()), "skadmin管理系统", content);
        }
        return emailVo;
    }

    /**
     * 验证
     *
     * @param code
     */
    public void validated(VerificationCode code) {
        VerificationCode verificationCode = verificationCodeRepository.findByScenesAndTypeAndValueAndStatusIsTrue(code.getScenes(), code.getType(), code.getValue());
        if (verificationCode == null || !verificationCode.getCode().equals(code.getCode())) {
            throw new BadRequestException("无效验证码");
        } else {
            verificationCode.setStatus(false);
            verificationCodeRepository.save(verificationCode);
        }
    }

    /**
     * 定时任务，指定分钟后改变验证码状态
     *
     * @param verifyCode
     */
    private void timedDestruction(VerificationCode verifyCode) {
        //以下示例为程序调用结束继续运行
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        try {
            executorService.schedule(() -> {
                verifyCode.setStatus(false);
                verificationCodeRepository.save(verifyCode);
            }, expiration * 60 * 1000L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
