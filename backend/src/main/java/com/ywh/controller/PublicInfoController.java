package com.ywh.controller;

import com.ywh.entity.*;
import com.ywh.enums.ComplianceStatus;
import com.ywh.enums.MeetingStage;
import com.ywh.repository.*;
import com.ywh.util.Result;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public-info")
@RequiredArgsConstructor
public class PublicInfoController {

    private final CommitteeMeetingRepository meetingRepo;
    private final MeetingPublishRepository publishRepo;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<CommitteeMeeting> committeePubs = meetingRepo
                .findByCommunityIdAndStageAndComplianceNotOrderByCreatedAtDesc(
                        communityId, MeetingStage.ended, ComplianceStatus.invalid);
        committeePubs = committeePubs.stream()
                .filter(m -> publishRepo.findByMeetingId(m.getId())
                        .map(MeetingPublish::getPublished).orElse(false))
                .toList();

        List<Map<String, Object>> items = committeePubs.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("type", "committee");
            item.put("id", m.getId());
            item.put("title", m.getTitle());
            item.put("date", m.getMeetingDate().toString());
            item.put("description", m.getDescription());
            item.put("publishDate", publishRepo.findByMeetingId(m.getId())
                    .map(p -> p.getPublishDate().toString()).orElse(""));
            return item;
        }).collect(Collectors.toList());

        items.sort((a, b) -> {
            String da = (String) a.getOrDefault("publishDate", "");
            String db = (String) b.getOrDefault("publishDate", "");
            return db.compareTo(da);
        });

        return Result.ok(items);
    }
}
