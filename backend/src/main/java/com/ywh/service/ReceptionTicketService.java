package com.ywh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywh.dto.MeetingTodoTicketVO;
import com.ywh.entity.ReceptionRecord;
import com.ywh.entity.UserRoleEntity;
import com.ywh.repository.ReceptionRecordRepository;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 把接待诉求派发到外部工单系统（「社区智能运维协同平台」）。
 * 与 MeetingTodoTicketService 走同一个外部接口、同一套配置，只是 payload 来源不同。
 * 接口规范见仓库根目录 external-ticket-api.md（注意该文档标着「接口设计稿，待后端按本文档实现」，
 * 且 base-url 指向对方系统，不是本项目 —— 对方未上线时这里会抛「工单系统连接失败」）。
 *
 * 0716 建：取代原先的内部派单流（dispatchToProperty → PropertyTasks.vue 物业侧工作台）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceptionTicketService {
    private final ReceptionRecordRepository recordRepo;
    private final ObjectMapper objectMapper;

    @Value("${external.ticket.enabled:false}") private boolean enabled;
    @Value("${external.ticket.simulate:false}") private boolean simulate;   // 演示模式：不外呼，本地受理
    @Value("${external.ticket.base-url:}") private String baseUrl;
    @Value("${external.ticket.uid:}") private String externalUid;
    @Value("${external.ticket.community-code:}") private String communityCode;
    @Value("${external.ticket.default-reporter-name:业委会接待}") private String defaultReporterName;
    @Value("${external.ticket.reception-ticket-type:居民诉求}") private String defaultTicketType;
    @Value("${external.ticket.default-priority:P2}") private String defaultPriority;
    @Value("${external.ticket.default-risk-level:LOW}") private String defaultRiskLevel;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Transactional
    public MeetingTodoTicketVO push(Long recordId) {
        if (!simulate) validateConfig();
        ReceptionRecord r = recordRepo.findById(recordId)
                .filter(record -> record.getCommunity() != null
                        && SecurityUtils.getCurrentCommunityId().equals(record.getCommunity().getId()))
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));

        // 幂等键：同一条接待重复点「派发工单」不会重复建单，对方按 uid+externalTicketNo 返回已有工单。
        String externalNo = "YWH-RECEPTION-" + recordId;
        // 模拟模式（演示/对方系统未上线）：不外呼，本地受理并生成稳定模拟单号；重复点幂等返回已有单
        if (simulate) {
            LocalDateTime now = LocalDateTime.now();
            if (blank(r.getTicketNo())) {
                r.setExternalTicketNo(externalNo);
                r.setTicketNo("GD" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-R" + String.format("%03d", recordId % 1000));
                r.setTicketPushedAt(now);
                recordRepo.save(r);
            }
            log.info("工单模拟模式：接待诉求本地受理 externalTicketNo={}, ticketNo={}", externalNo, r.getTicketNo());
            return MeetingTodoTicketVO.builder()
                    .created(true).externalTicketNo(externalNo).ticketNo(r.getTicketNo())
                    .status("ACCEPTED").statusLabel("已受理")
                    .pushedAt((r.getTicketPushedAt() == null ? now : r.getTicketPushedAt())
                            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm")))
                    .build();
        }
        UserRoleEntity actor = SecurityUtils.getCurrentUserRole();
        String reporter = actor != null && actor.getRealName() != null ? actor.getRealName() : defaultReporterName;
        String location = blank(r.getRoom()) ? r.getCommunity().getName() : r.getCommunity().getName() + r.getRoom();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalTicketNo", externalNo);
        payload.put("communityCode", communityCode);
        payload.put("title", clip(title(r), 256));
        payload.put("description", clip(description(r), 4000));
        payload.put("location", clip(location, 256));
        payload.put("reporterName", reporter);
        payload.put("ticketType", defaultTicketType);
        payload.put("riskLevel", defaultRiskLevel);
        payload.put("priority", defaultPriority);
        payload.put("remark", "来源：业委会接待登记（接待记录ID " + recordId + "）");

        long started = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(baseUrl) + "/api/external/v1/tickets"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-External-Uid", externalUid)
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || root.path("code").asInt() != 200) {
                String message = root.path("message").asText("工单系统返回异常");
                throw new IllegalStateException(message);
            }
            JsonNode data = root.path("data");
            JsonNode ticket = data.path("ticket");
            LocalDateTime now = LocalDateTime.now();
            r.setExternalTicketNo(externalNo);
            r.setTicketNo(text(ticket, "ticketNo"));
            r.setTicketPushedAt(now);
            recordRepo.save(r);
            log.info("接待诉求推送工单成功 externalUid={}, externalTicketNo={}, communityCode={}, ticketNo={}, costMs={}",
                    externalUid, externalNo, communityCode, r.getTicketNo(), System.currentTimeMillis() - started);
            return MeetingTodoTicketVO.builder()
                    .created(data.path("created").asBoolean(false))
                    .externalTicketNo(externalNo)
                    .ticketNo(r.getTicketNo())
                    .ticketId(ticket.path("id").isNumber() ? ticket.path("id").asLong() : null)
                    .status(text(ticket, "status"))
                    .statusLabel(text(ticket, "statusLabel"))
                    .pushedAt(now.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")))
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("工单推送已中断");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("接待诉求推送工单失败 externalTicketNo={}, costMs={}", externalNo, System.currentTimeMillis() - started, e);
            throw new IllegalStateException("工单系统连接失败，请稍后重试");
        }
    }

    private void validateConfig() {
        if (!enabled) throw new IllegalStateException("工单系统对接尚未启用");
        if (blank(baseUrl) || blank(externalUid) || blank(communityCode)) {
            throw new IllegalStateException("工单系统配置不完整");
        }
    }

    private String title(ReceptionRecord r) {
        String who = blank(r.getVisitorName()) ? "居民" : r.getVisitorName();
        String what = blank(r.getContent()) ? "来访诉求" : r.getContent();
        // 标题取诉求首行，太长的正文交给 description
        int nl = what.indexOf('\n');
        if (nl > 0) what = what.substring(0, nl);
        return who + "反映：" + what;
    }

    private String description(ReceptionRecord r) {
        StringBuilder s = new StringBuilder("业委会接待登记的居民诉求。");
        if (!blank(r.getContent())) s.append("\n诉求内容：").append(r.getContent());
        if (r.getCategory() != null) s.append("\n诉求分类：").append(r.getCategory().getLabel());
        if (!blank(r.getVisitorName())) s.append("\n来访人：").append(r.getVisitorName());
        if (!blank(r.getRoom())) s.append("\n房号：").append(r.getRoom());
        if (r.getDate() != null) s.append("\n来访日期：").append(r.getDate());
        if (!blank(r.getReceiver())) s.append("\n接待人：").append(r.getReceiver());
        return s.toString();
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return blank(v) ? null : v;
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String trimSlash(String s) { return s.replaceAll("/+$", ""); }
    private static String clip(String s, int n) { return s != null && s.length() > n ? s.substring(0, n) : s; }
}
