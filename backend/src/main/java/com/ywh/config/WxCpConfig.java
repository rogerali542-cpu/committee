package com.ywh.config;

import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 企业微信（WeCom）JS-SDK 配置。用于给前端签发 wx.config 的签名，
 * 让「图片」上传在企业微信内置浏览器里能调起相册多选 chooseImage
 * （企业微信 X5 内核会拦截 &lt;input type=file multiple&gt; 退化成单选）。
 *
 * 凭证走 application.yml 的 wecom.* / 环境变量；未配置时 corp-id/secret 为空，
 * 仅当 wecom.enabled=true 且填了凭证时前端才实际调用（否则自动回退系统选择器）。
 */
@Configuration
public class WxCpConfig {

    @Value("${wecom.corp-id:}")
    private String corpId;

    @Value("${wecom.secret:}")
    private String secret;

    @Value("${wecom.agent-id:0}")
    private Integer agentId;

    @Bean
    public WxCpService wxCpService() {
        WxCpDefaultConfigImpl config = new WxCpDefaultConfigImpl();
        config.setCorpId(corpId);
        config.setCorpSecret(secret);
        config.setAgentId(agentId);

        WxCpService service = new WxCpServiceImpl();
        service.setWxCpConfigStorage(config);
        return service;
    }
}
