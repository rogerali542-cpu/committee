package com.ywh.config;

import com.ywh.entity.Community;
import com.ywh.entity.Notice;
import com.ywh.repository.CommunityRepository;
import com.ywh.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 预置小区公告示例数据。Flyway 已禁用、用 ddl-auto，故启动时为「尚无公告」的小区插入几条示例，
 * 让首页公告卡片开箱即有真实数据。幂等：已有公告的小区不再插入。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class NoticeSeeder implements CommandLineRunner {

    private final CommunityRepository communityRepo;
    private final NoticeRepository noticeRepo;

    private record Seed(String date, String title, String detail) {}

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("2026-06-20", "关于小区电梯年度维保的通知",
                    "定于6月28日对1-8栋电梯进行年度安全维保，届时每栋将轮流停梯约2小时，请各位业主提前做好准备。"),
            new Seed("2026-06-15", "地下车库照明改造施工公告",
                    "6月18日-7月5日对地下车库B1/B2层进行LED照明节能改造，施工期间部分车位将临时封闭，请按现场指引停放车辆。"),
            new Seed("2026-06-10", "小区绿化养护工作安排",
                    "6月12日起对小区公共绿地进行夏季修剪打药，请各位业主在此期间关好门窗，看护好儿童和宠物。"),
            new Seed("2026-06-05", "物业费缴纳提醒",
                    "2026年第三季度物业费已开始收缴，请于7月15日前通过微信小程序或前往物业服务中心缴纳，逾期将产生滞纳金。"),
            new Seed("2026-05-28", "关于小区门禁系统升级的通知",
                    "6月1日起小区东门、南门门禁将升级为人脸识别+刷卡双模式，请未录入人脸信息的业主携带身份证到物业服务中心办理。")
    );

    @Override
    public void run(String... args) {
        List<Community> communities = communityRepo.findAll();
        for (Community community : communities) {
            if (noticeRepo.countByCommunityId(community.getId()) > 0) {
                continue;
            }
            for (Seed s : DEFAULTS) {
                noticeRepo.save(Notice.builder()
                        .community(community)
                        .noticeDate(LocalDate.parse(s.date()))
                        .title(s.title())
                        .detail(s.detail())
                        .published(true)
                        .build());
            }
            log.info("[NoticeSeeder] 已为小区 {} (id={}) 预置 {} 条示例公告",
                    community.getName(), community.getId(), DEFAULTS.size());
        }
    }
}
