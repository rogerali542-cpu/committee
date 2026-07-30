package com.ywh.config;

import com.ywh.entity.Community;
import com.ywh.entity.CommitteeMeeting;
import com.ywh.entity.MeetingTodo;
import com.ywh.entity.ReceptionRecord;
import com.ywh.enums.MeetingStage;
import com.ywh.enums.ReceptionCategory;
import com.ywh.repository.CommitteeMeetingRepository;
import com.ywh.repository.CommunityRepository;
import com.ywh.repository.MeetingTodoRepository;
import com.ywh.repository.ReceptionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 演示待办补种（0731）：驾驶舱「待办事项 · N 项」与业委会待办聚合页上线后需要开箱有数。
 * 老库已有接待记录时 ReceptionLearningSeeder 整体跳过，这里改为按 sessionKey 逐条幂等，
 * 补两条「未办结」接待事项（一条已交物业=处理中、一条待跟进）；
 * 会议结构化待办表为空时，给最近一场会议补三条示例待办（含一条已完成，档案馆「已办」页签同样有数）。
 * 演示用，非真实数据。
 */
@Slf4j
@Component
@Order(22)
@RequiredArgsConstructor
public class DemoTodoSeeder implements CommandLineRunner {

    private final CommunityRepository communityRepo;
    private final ReceptionRecordRepository receptionRepo;
    private final CommitteeMeetingRepository meetingRepo;
    private final MeetingTodoRepository todoRepo;

    @Override
    public void run(String... args) {
        for (Community c : communityRepo.findAll()) {
            seedPendingReceptions(c);
            seedMeetingTodos(c);
        }
    }

    /** 未办结接待事项（resolution 空且未办结时间为空＝待跟进/处理中），按 sessionKey 逐条幂等。 */
    private void seedPendingReceptions(Community c) {
        List<ReceptionRecord> existing = receptionRepo.findByCommunityIdOrderByDateDescTimeDesc(c.getId());
        if (existing.stream().noneMatch(r -> "demo-pending-2026-06-25".equals(r.getSessionKey()))) {
            receptionRepo.save(ReceptionRecord.builder()
                    .community(c).date(LocalDate.parse("2026-06-25")).time(LocalTime.parse("19:20"))
                    .sessionKey("demo-pending-2026-06-25")
                    .visitorName("李桂芬").room("3号楼402").receiver("赵敏")
                    .category(ReceptionCategory.property)
                    .content("3号楼下水管返味，异味明显，希望排查立管。")
                    .resolution("")
                    .propertyTransferredAt(LocalDateTime.of(2026, 6, 26, 10, 0))   // 已交物业 → 处理中
                    .build());
            log.info("[DemoTodoSeeder] 小区 {} 补种未办结接待：下水管返味（处理中）", c.getName());
        }
        if (existing.stream().noneMatch(r -> "demo-pending-2026-06-18".equals(r.getSessionKey()))) {
            receptionRepo.save(ReceptionRecord.builder()
                    .community(c).date(LocalDate.parse("2026-06-18")).time(LocalTime.parse("19:05"))
                    .sessionKey("demo-pending-2026-06-18")
                    .visitorName("王志强").room("地下车库B1").receiver("李秀英")
                    .category(ReceptionCategory.property)
                    .content("地下车库照明不足，夜间昏暗存在安全隐患。")
                    .resolution("")
                    .build());
            log.info("[DemoTodoSeeder] 小区 {} 补种未办结接待：车库照明不足（待跟进）", c.getName());
        }
    }

    /** 会议结构化待办：全表为空才补（已有真实固化数据则不动），优先挂已结束的那场会议。 */
    private void seedMeetingTodos(Community c) {
        if (todoRepo.count() > 0) {
            return;
        }
        List<CommitteeMeeting> meetings = meetingRepo.findByCommunityIdOrderByCreatedAtDesc(c.getId());
        if (meetings.isEmpty()) {
            log.info("[DemoTodoSeeder] 小区 {} 尚无会议，跳过会议待办补种", c.getName());
            return;
        }
        CommitteeMeeting m = meetings.stream()
                .filter(x -> x.getStage() == MeetingStage.ended)
                .findFirst().orElse(meetings.get(0));
        LocalDateTime now = LocalDateTime.now();
        todoRepo.save(MeetingTodo.builder().meetingId(m.getId())
                .title("公区保洁合同续签比价，收齐三家报价")
                .owner("王志强").status("todo").sortOrder(1)
                .sourceRef("物业服务质量与保洁合同议题").createdAt(now).build());
        todoRepo.save(MeetingTodo.builder().meetingId(m.getId())
                .title("电梯加装意见征询表回收与汇总")
                .owner("李桂芬").status("doing").sortOrder(2)
                .sourceRef("电梯加装征询议题").createdAt(now).build());
        todoRepo.save(MeetingTodo.builder().meetingId(m.getId())
                .title("会议纪要在小区公告栏张贴公示")
                .owner("张建国").status("done").sortOrder(3)
                .lastActorName("张建国").updatedAt(now).createdAt(now).build());
        log.info("[DemoTodoSeeder] 小区 {} 为会议「{}」(id={}) 补种 3 条示例待办", c.getName(), m.getTitle(), m.getId());
    }
}
