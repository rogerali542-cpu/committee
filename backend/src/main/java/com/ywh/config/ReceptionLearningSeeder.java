package com.ywh.config;

import com.ywh.entity.Community;
import com.ywh.entity.LearningRecord;
import com.ywh.entity.ReceptionRecord;
import com.ywh.enums.LearningType;
import com.ywh.enums.MeetingStage;
import com.ywh.enums.ReceptionCategory;
import com.ywh.repository.CommunityRepository;
import com.ywh.repository.LearningRecordRepository;
import com.ywh.repository.ReceptionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 预置「接待记录」「学习培训」示例数据。Flyway 已禁用、用 ddl-auto，故启动时为尚无数据的小区
 * 各插入几条示例，让这两个页面开箱即有内容。幂等：已有记录的小区不再插入。
 * 演示用，非真实数据。
 */
@Slf4j
@Component
@Order(21)
@RequiredArgsConstructor
public class ReceptionLearningSeeder implements CommandLineRunner {

    private final CommunityRepository communityRepo;
    private final ReceptionRecordRepository receptionRepo;
    private final LearningRecordRepository learningRepo;

    @Override
    public void run(String... args) {
        for (Community c : communityRepo.findAll()) {
            seedReception(c);
            seedLearning(c);
        }
    }

    private void seedReception(Community c) {
        if (!receptionRepo.findByCommunityIdOrderByDateDescTimeDesc(c.getId()).isEmpty()) {
            return; // 已有接待记录，跳过
        }
        // 1) 物业类，待派单（未办结）
        receptionRepo.save(ReceptionRecord.builder()
                .community(c)
                .date(LocalDate.parse("2026-06-26")).time(LocalTime.parse("15:30"))
                .visitorName("周建国").room("5号楼801").receiver("王秀兰（副主任）")
                .category(ReceptionCategory.property)
                .content("高层下午用水高峰水压不足，经常断水。")
                .resolution("")
                .propertyStatus("pending_dispatch")
                .fedProperty(false).fedOwner(false)
                .build());
        // 2) 公共事务，待办结
        receptionRepo.save(ReceptionRecord.builder()
                .community(c)
                .date(LocalDate.parse("2026-06-24")).time(LocalTime.parse("10:30"))
                .visitorName("李红梅").room("12号楼304").receiver("张伟（委员）")
                .category(ReceptionCategory.public_affairs)
                .content("希望在中心花园增设儿童活动区和健身器材。")
                .resolution("")
                .fedProperty(false).fedOwner(false)
                .build());
        // 3) 邻里纠纷，已办结
        receptionRepo.save(ReceptionRecord.builder()
                .community(c)
                .date(LocalDate.parse("2026-06-20")).time(LocalTime.parse("16:00"))
                .visitorName("吴强").room("3号楼1602").receiver("陈志远（委员）")
                .category(ReceptionCategory.neighbor)
                .content("楼道长期堆放杂物，影响通行和消防安全。")
                .resolution("已张贴清理告示并清运，提醒业主勿占用公共区域。")
                .fedProperty(false).fedOwner(true)
                .build());
        log.info("[ReceptionLearningSeeder] 已为小区 {} (id={}) 预置 3 条示例接待记录", c.getName(), c.getId());
    }

    private void seedLearning(Community c) {
        if (!learningRepo.findByCommunityIdOrderByDateDesc(c.getId()).isEmpty()) {
            return; // 已有学习培训，跳过
        }
        // 1) 内部学习，待开（默认「内部学习」标签即可见）
        learningRepo.save(LearningRecord.builder()
                .community(c).title("消防安全与应急疏散专题学习")
                .date(LocalDate.parse("2026-06-28")).time(LocalTime.parse("14:00"))
                .location("物业培训室").trainer("消防大队·李教官")
                .type(LearningType.internal).stage(MeetingStage.preparing).progress(0)
                .attendees("全体委员").notified(false)
                .build());
        // 2) 内部学习，已结束（计入年度已完成，进度环非空）
        learningRepo.save(LearningRecord.builder()
                .community(c).title("老旧小区加装电梯政策宣讲")
                .date(LocalDate.parse("2026-05-10")).time(LocalTime.parse("10:00"))
                .location("小区会议室").trainer("区住建委·钱主任")
                .type(LearningType.internal).stage(MeetingStage.ended).progress(100)
                .attendees("全体委员").notified(true)
                .build());
        // 3) 街镇培训，待开（默认「外部培训」标签即可见）
        learningRepo.save(LearningRecord.builder()
                .community(c).title("街道业委会规范化运作培训")
                .date(LocalDate.parse("2026-06-22")).time(LocalTime.parse("09:30"))
                .location("街道办事处会议室").trainer("街道物管科·周老师")
                .type(LearningType.street).stage(MeetingStage.preparing).progress(0)
                .attendees("主任、副主任").notified(false)
                .build());
        log.info("[ReceptionLearningSeeder] 已为小区 {} (id={}) 预置 3 条示例学习培训", c.getName(), c.getId());
    }
}
