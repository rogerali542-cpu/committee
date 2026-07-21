package com.ywh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywh.dto.MeetingTodoTicketVO;
import com.ywh.entity.CommitteeMeeting;
import com.ywh.entity.MeetingTodo;
import com.ywh.entity.UserRoleEntity;
import com.ywh.repository.CommitteeMeetingRepository;
import com.ywh.repository.MeetingTodoRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingTodoTicketService {
    private final MeetingTodoRepository todoRepo;
    private final CommitteeMeetingRepository meetingRepo;
    private final ObjectMapper objectMapper;

    @Value("${external.ticket.enabled:false}") private boolean enabled;
    @Value("${external.ticket.base-url:}") private String baseUrl;
    @Value("${external.ticket.uid:}") private String externalUid;
    @Value("${external.ticket.community-code:}") private String communityCode;
    @Value("${external.ticket.default-reporter-name:业委会会议}") private String defaultReporterName;
    @Value("${external.ticket.default-ticket-type:会议待办}") private String defaultTicketType;
    @Value("${external.ticket.default-priority:P2}") private String defaultPriority;
    @Value("${external.ticket.default-risk-level:LOW}") private String defaultRiskLevel;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Transactional
    public MeetingTodoTicketVO push(Long meetingId, Long todoId) {
        validateConfig();
        MeetingTodo todo = todoRepo.findById(todoId)
                .orElseThrow(() -> new IllegalArgumentException("待办不存在"));
        if (!todo.getMeetingId().equals(meetingId)) throw new IllegalArgumentException("待办与会议不匹配");
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));

        String externalNo = "YWH-MEETING-" + meetingId + "-TODO-" + todoId;
        UserRoleEntity actor = SecurityUtils.getCurrentUserRole();
        String reporter = actor != null && actor.getRealName() != null ? actor.getRealName() : defaultReporterName;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalTicketNo", externalNo);
        payload.put("communityCode", communityCode);
        payload.put("title", clip(todo.getTitle(), 256));
        payload.put("description", clip(description(meeting, todo), 4000));
        payload.put("location", clip(blank(meeting.getLocation()) ? meeting.getCommunity().getName() : meeting.getLocation(), 256));
        payload.put("reporterName", reporter);
        payload.put("ticketType", defaultTicketType);
        payload.put("riskLevel", defaultRiskLevel);
        payload.put("priority", defaultPriority);
        String deadline = parseDeadline(todo.getDueText());
        if (deadline != null) payload.put("deadline", deadline);
        payload.put("remark", "来源：" + meeting.getTitle() + "（会议待办ID " + todoId + "）");

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
            todo.setExternalTicketNo(externalNo);
            todo.setTicketNo(text(ticket, "ticketNo"));
            todo.setTicketPushedAt(now);
            todoRepo.save(todo);
            log.info("会议待办推送工单成功 externalUid={}, externalTicketNo={}, communityCode={}, ticketNo={}, costMs={}",
                    externalUid, externalNo, communityCode, todo.getTicketNo(), System.currentTimeMillis() - started);
            return MeetingTodoTicketVO.builder()
                    .created(data.path("created").asBoolean(false))
                    .externalTicketNo(externalNo)
                    .ticketNo(todo.getTicketNo())
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
            log.error("会议待办推送工单失败 externalTicketNo={}, costMs={}", externalNo, System.currentTimeMillis() - started, e);
            throw new IllegalStateException("工单系统连接失败，请稍后重试");
        }
    }

    private void validateConfig() {
        if (!enabled) throw new IllegalStateException("工单系统对接尚未启用");
        if (blank(baseUrl) || blank(externalUid) || blank(communityCode)) {
            throw new IllegalStateException("工单系统配置不完整");
        }
    }

    private String description(CommitteeMeeting meeting, MeetingTodo todo) {
        StringBuilder s = new StringBuilder("会议待办：").append(todo.getTitle());
        s.append("\n来源会议：").append(meeting.getTitle());
        if (!blank(todo.getOwner())) s.append("\n负责人：").append(todo.getOwner());
        if (!blank(todo.getDueText())) s.append("\n截止时间：").append(todo.getDueText());
        return s.toString();
    }

    private String parseDeadline(String value) {
        if (blank(value)) return null;
        Matcher m = Pattern.compile("(20\\d{2})[-年/](\\d{1,2})[-月/](\\d{1,2})日?").matcher(value);
        if (!m.find()) return null;
        try {
            LocalDate d = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            return LocalDateTime.of(d, LocalTime.of(18, 0)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) { return null; }
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return blank(v) ? null : v;
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String trimSlash(String s) { return s.replaceAll("/+$", ""); }
    private static String clip(String s, int n) { return s != null && s.length() > n ? s.substring(0, n) : s; }
}
