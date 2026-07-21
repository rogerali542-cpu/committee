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
        // 每个非节假日接待周均生成一条台账；无人来访也留档，但不计入接待人次。
        seedReceptionRow(c, "2026-02-01", "王海", "2号楼602", ReceptionCategory.property,
                "楼道照明灯损坏，希望安排检修。", "已联系物业更换灯具。");
        seedReceptionRow(c, "2026-02-08", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-03-01", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-03-08", "刘芳", "6号楼1201", ReceptionCategory.public_affairs,
                "建议增加非机动车充电区域。", "已提交业委会讨论并纳入改造计划。");
        seedReceptionRow(c, "2026-03-15", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-03-22", "赵明", "9号楼503", ReceptionCategory.neighbor,
                "楼上夜间噪音较大，希望协助沟通。", "已组织双方沟通并达成作息约定。");
        seedReceptionRow(c, "2026-03-29", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-04-12", "陈丽", "3号楼901", ReceptionCategory.property,
                "单元门禁无法正常闭合。", "已转物业完成门禁维修。");
        seedReceptionRow(c, "2026-04-19", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-04-26", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-05-10", "周强", "12号楼304", ReceptionCategory.public_affairs,
                "希望中心花园增加儿童活动设施。", "已纳入公共设施改造议题。");
        seedReceptionRow(c, "2026-05-17", "孙梅", "5号楼801", ReceptionCategory.property,
                "高峰时段水压不足。", "物业已完成二次供水设备检查。");
        seedReceptionRow(c, "2026-05-24", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-05-31", "吴刚", "7号楼1602", ReceptionCategory.neighbor,
                "楼道堆物影响消防通行。", "已清运杂物并张贴消防提示。");
        seedReceptionRow(c, "2026-06-07", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-06-14", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        seedReceptionRow(c, "2026-06-28", "李红梅", "15号楼1801", ReceptionCategory.property,
                "顶楼雨天渗水，希望尽快维修。", "");
        seedReceptionRow(c, "2026-07-05", "郑海", "11号楼1203", ReceptionCategory.property,
                "电梯运行时有异响，希望安排检查。", "已联系维保单位完成检查。");
        seedReceptionRow(c, "2026-07-12", "无人来访", "", ReceptionCategory.public_affairs,
                "本次接待无居民来访", "无需处理");
        log.info("[ReceptionLearningSeeder] 已为小区 {} (id={}) 预置 19 条非节假日按周分布的示例接待记录", c.getName(), c.getId());
    }

    private void seedReceptionRow(Community community, String date, String visitorName, String room,
                                  ReceptionCategory category, String content, String resolution) {
        receptionRepo.save(ReceptionRecord.builder()
                .community(community)
                .date(LocalDate.parse(date))
                .time(LocalTime.parse("15:00"))
                .sessionKey("demo-weekly-" + date)
                .visitorName(visitorName)
                .room(room)
                .receiver("李秀英")
                .category(category)
                .content(content)
                .resolution(resolution)
                .build());
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
