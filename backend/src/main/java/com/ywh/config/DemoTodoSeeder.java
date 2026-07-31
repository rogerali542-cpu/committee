package com.ywh.config;

import com.ywh.entity.Community;
import com.ywh.entity.CommitteeMeeting;
import com.ywh.entity.MeetingTodo;
import com.ywh.entity.ReceptionRecord;
import com.ywh.entity.ReceptionSystem;
import com.ywh.enums.MeetingStage;
import com.ywh.enums.ReceptionCategory;
import com.ywh.repository.CommitteeMeetingRepository;
import com.ywh.repository.CommunityRepository;
import com.ywh.repository.MeetingTodoRepository;
import com.ywh.repository.ReceptionRecordRepository;
import com.ywh.repository.ReceptionSystemRepository;
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
 * 演示待办补种/收敛（0731）：驾驶舱「待办事项 · N 项」与业委会待办聚合页需要开箱有数，但项数要克制——
 * 0731 用户定：常态 0-1 项，演示保留「2 项」即可（1 条业主接待 待跟进 + 1 条会议议题 处理中），5 项太多。
 * 因此本 seeder 既「补」也「收敛」（新旧库、每次启动都幂等跑）：
 *  · 接待：保留 1 条待跟进（王志强 车库照明）；把旧演示多出的 2 条（李桂芬 下水返味、李红梅 顶楼渗水）填结果办结；
 *  · 会议：留 1 条未办（电梯征询 处理中）+ 2 条已办；把「保洁合同比价」从未办收敛为已办。
 * 演示用，非真实数据。
 */
@Slf4j
@Component
@Order(22)
@RequiredArgsConstructor
public class DemoTodoSeeder implements CommandLineRunner {

    private final CommunityRepository communityRepo;
    private final ReceptionRecordRepository receptionRepo;
    private final ReceptionSystemRepository receptionSystemRepo;
    private final CommitteeMeetingRepository meetingRepo;
    private final MeetingTodoRepository todoRepo;

    @Override
    public void run(String... args) {
        for (Community c : communityRepo.findAll()) {
            seedReceptionSystem(c);
            seedPendingReceptions(c);
            seedMeetingTodos(c);
        }
        logPendingSummary();
    }

    /** 自检日志（0731）：打印收敛后仍未办结的待办明细与合计，直接对上首页「待办 · N 项」。
     *  重启后端后在控制台看这几行——若合计不是 2，明细会指出多出来的是哪条（含真实数据/新增项）。 */
    private void logPendingSummary() {
        int rec = 0, mtg = 0;
        for (ReceptionRecord r : receptionRepo.findAll()) {
            if ("无人来访".equals(r.getVisitorName())) continue;
            if (r.getResolution() == null || r.getResolution().trim().isEmpty()) {
                rec++;
                log.info("[DemoTodoSeeder]   · 接待未办：{} / {}", r.getVisitorName(), r.getContent());
            }
        }
        for (MeetingTodo t : todoRepo.findAll()) {
            if (!"done".equals(t.getStatus())) {
                mtg++;
                log.info("[DemoTodoSeeder]   · 会议未办：{}（{}）", t.getTitle(), t.getStatus());
            }
        }
        log.info("[DemoTodoSeeder] ===== 收敛后待办自检：接待未办 {} + 会议未办 {} = 合计 {} 项 =====", rec, mtg, rec + mtg);
    }

    /** 接待时间安排默认预置（0731）：此前无任何 seeder 种它，库重建后「每周四接待」就消失，
     *  接待页/驾驶舱都会退成「还没设置接待时间」。有记录则不动（尊重用户手动设置）。 */
    private void seedReceptionSystem(Community c) {
        if (receptionSystemRepo.findByCommunityId(c.getId()).isPresent()) {
            return;
        }
        receptionSystemRepo.save(ReceptionSystem.builder()
                .community(c)
                .published(true)
                .timeDesc("每周四 19:00—20:00，法定节假日暂停")
                .place(c.getName() + "党群服务站一楼接待室")
                .person("当值委员轮值")
                .updatedAt(LocalDateTime.now())
                .build());
        log.info("[DemoTodoSeeder] 小区 {} 预置默认接待安排：每周四 19:00—20:00", c.getName());
    }

    /** 演示接待收敛为「1 条待跟进」：保留王志强（车库照明）幂等新增；把旧演示多出的待跟进接待填结果办结。 */
    private void seedPendingReceptions(Community c) {
        List<ReceptionRecord> existing = receptionRepo.findByCommunityIdOrderByDateDescTimeDesc(c.getId());
        // 保留 1 条「待跟进」接待作演示（王志强 车库照明），按 sessionKey 幂等新增
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
        // 收敛（0731 用户定：待办从 5 降到 2）——把此前多出的两条待跟进接待填处理结果办结：
        // 旧演示的「下水管返味」(李桂芬) 与接待台账里的「顶楼渗水」(李红梅)。仅动这两条已知演示行。
        for (ReceptionRecord r : existing) {
            boolean pending = r.getResolution() == null || r.getResolution().trim().isEmpty();
            if (!pending) continue;
            if ("demo-pending-2026-06-25".equals(r.getSessionKey())) {
                r.setResolution("已联系物业排查检修 3 号楼下水立管，返味已消除。");
                receptionRepo.save(r);
                log.info("[DemoTodoSeeder] 小区 {} 收敛演示接待：下水管返味 → 已办结", c.getName());
            } else if ("李红梅".equals(r.getVisitorName())) {
                r.setResolution("已转物业安排顶楼防水维修，完工后回访确认。");
                receptionRepo.save(r);
                log.info("[DemoTodoSeeder] 小区 {} 收敛演示接待：顶楼渗水 → 已办结", c.getName());
            }
        }
    }

    /** 会议结构化待办：全表为空才补（1 未办 + 2 已办）；并把「保洁合同比价」收敛为已办，使会议侧未办只剩 1 条。 */
    private void seedMeetingTodos(Community c) {
        if (todoRepo.count() == 0) {
            List<CommitteeMeeting> meetings = meetingRepo.findByCommunityIdOrderByCreatedAtDesc(c.getId());
            if (meetings.isEmpty()) {
                log.info("[DemoTodoSeeder] 小区 {} 尚无会议，跳过会议待办补种", c.getName());
            } else {
                CommitteeMeeting m = meetings.stream()
                        .filter(x -> x.getStage() == MeetingStage.ended)
                        .findFirst().orElse(meetings.get(0));
                LocalDateTime now = LocalDateTime.now();
                // 仅 1 条未办（电梯征询 处理中）作演示；另两条已办，档案馆「已办」页签有数
                todoRepo.save(MeetingTodo.builder().meetingId(m.getId())
                        .title("电梯加装意见征询表回收与汇总")
                        .owner("李桂芬").status("doing").sortOrder(1)
                        .sourceRef("电梯加装征询议题").createdAt(now).build());
                todoRepo.save(MeetingTodo.builder().meetingId(m.getId())
                        .title("公区保洁合同续签比价，收齐三家报价")
                        .owner("王志强").status("done").sortOrder(2)
                        .lastActorName("王志强").updatedAt(now)
                        .sourceRef("物业服务质量与保洁合同议题").createdAt(now).build());
                todoRepo.save(MeetingTodo.builder().meetingId(m.getId())
                        .title("会议纪要在小区公告栏张贴公示")
                        .owner("张建国").status("done").sortOrder(3)
                        .lastActorName("张建国").updatedAt(now).createdAt(now).build());
                log.info("[DemoTodoSeeder] 小区 {} 为会议「{}」(id={}) 补种 1 未办 + 2 已办 示例待办", c.getName(), m.getTitle(), m.getId());
            }
        }
        // 收敛（0731 用户定：新旧库都跑）——把「保洁合同比价」从未办结收敛为已办，会议侧未办只留「电梯征询」1 条
        LocalDateTime now = LocalDateTime.now();
        for (MeetingTodo t : todoRepo.findAll()) {
            if (t.getTitle() != null && t.getTitle().contains("保洁合同") && !"done".equals(t.getStatus())) {
                t.setStatus("done");
                t.setLastActorName("张建国");
                t.setUpdatedAt(now);
                todoRepo.save(t);
                log.info("[DemoTodoSeeder] 小区 {} 收敛会议待办：「{}」→ 已办", c.getName(), t.getTitle());
            }
        }
    }
}
