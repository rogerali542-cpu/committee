package com.ywh.controller;

import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxJsapiSignature;
import me.chanjar.weixin.cp.api.WxCpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信 JS-SDK 签名接口。前端在企业微信内置浏览器里调 wx.config 前先取签名，
 * 之后即可调 chooseImage 相册多选。签名内容不含机密（由公开 URL + jsapi_ticket 计算），
 * 故放开鉴权；未启用/未配凭证时返回 400，前端据此回退系统文件选择器。
 */
@Slf4j
@RestController
@RequestMapping("/api/wecom")
@RequiredArgsConstructor
public class WecomController {

    private final WxCpService wxCpService;

    @Value("${wecom.enabled:false}")
    private boolean enabled;

    /**
     * @param url 调用页面的完整 URL（不含 # 及其后部分）。前端传当前页/进入页 URL，须与签名严格一致。
     */
    @GetMapping("/js-config")
    public Result<Map<String, Object>> jsConfig(@RequestParam String url) {
        if (!enabled) {
            return Result.fail(400, "企业微信 JS-SDK 未启用");
        }
        try {
            WxJsapiSignature sig = wxCpService.createJsapiSignature(url);
            Map<String, Object> m = new HashMap<>();
            m.put("corpId", sig.getAppId());
            m.put("timestamp", sig.getTimestamp());
            m.put("nonceStr", sig.getNonceStr());
            m.put("signature", sig.getSignature());
            return Result.ok(m);
        } catch (Exception e) {
            log.warn("[wecom] 生成 JS-SDK 签名失败 url={}", url, e);
            return Result.fail(500, "生成企业微信签名失败：" + e.getMessage());
        }
    }
}
