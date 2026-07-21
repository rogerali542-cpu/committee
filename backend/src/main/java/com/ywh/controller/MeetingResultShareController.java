package com.ywh.controller;

import com.ywh.entity.CommitteeMeeting;
import com.ywh.repository.CommitteeMeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/share/committee-result")
@RequiredArgsConstructor
public class MeetingResultShareController {
    private final CommitteeMeetingRepository meetingRepository;

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resultCard(@PathVariable Long id) {
        CommitteeMeeting meeting = meetingRepository.findById(id).orElse(null);
        String meetingTitle = meeting == null || meeting.getTitle() == null || meeting.getTitle().isBlank()
                ? "线上会议" : meeting.getTitle();
        String title = escape(meetingTitle + "结果确认");
        String description = "请参会委员点击卡片，进入会议结果页面完成本人确认";
        String origin = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String shareUrl = origin + "/api/share/committee-result/" + id;
        String image = shareUrl + "/cover.svg";
        String target = origin + "/meeting-live-quick?meetingId=" + id + "&card=1";
        String html = """
                <!doctype html><html lang="zh-CN"><head>
                <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title>
                <meta name="description" content="%s">
                <meta property="og:type" content="website">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:image" content="%s">
                <meta property="og:url" content="%s">
                <meta name="twitter:card" content="summary">
                <meta http-equiv="refresh" content="0;url=%s">
                </head><body><p>正在进入会议结果确认页面…</p>
                <script>location.replace('%s')</script></body></html>
                """.formatted(title, description, title, description, image, shareUrl, target, target);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping(value = "/{id}/cover.svg", produces = "image/svg+xml")
    public ResponseEntity<String> cover(@PathVariable Long id) {
        CommitteeMeeting meeting = meetingRepository.findById(id).orElse(null);
        String title = meeting == null || meeting.getTitle() == null ? "线上会议" : meeting.getTitle();
        if (title.length() > 14) title = title.substring(0, 14) + "…";
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="480" height="384" viewBox="0 0 480 384">
                  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                    <stop stop-color="#285f7d"/><stop offset="1" stop-color="#88b2c5"/>
                  </linearGradient></defs>
                  <rect width="480" height="384" rx="28" fill="url(#g)"/>
                  <circle cx="78" cy="76" r="36" fill="none" stroke="#fff" stroke-width="3" opacity=".9"/>
                  <text x="78" y="88" fill="#fff" font-size="34" text-anchor="middle" font-family="sans-serif">议</text>
                  <text x="42" y="190" fill="#fff" font-size="32" font-weight="700" font-family="sans-serif">%s</text>
                  <text x="42" y="240" fill="#e8f2f6" font-size="25" font-family="sans-serif">会议结果确认</text>
                  <path d="M42 300H438M42 326H320" stroke="#fff" stroke-width="5" opacity=".55"/>
                </svg>
                """.formatted(escape(title));
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(svg);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
