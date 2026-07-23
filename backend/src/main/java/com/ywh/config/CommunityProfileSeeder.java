package com.ywh.config;

import com.ywh.entity.Community;
import com.ywh.repository.CommunityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 演示小区「户口」默认值（0723 用户定，全虚构，勿用真实地名）：
 * 江州市望江区阳光花园小区，业委会第一届（备案任期 2025.6—2030.6，备案证号 江望房物备〔2025〕第012号——仅记档，不入库）。
 * 落款全称由此拼出：「江州市望江区阳光花园业主委员会（第一届）」。
 * 幂等：只补空值，已有值（后续在管理后台改过）不覆盖。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class CommunityProfileSeeder implements CommandLineRunner {

    private final CommunityRepository communityRepo;

    @Override
    public void run(String... args) {
        for (Community c : communityRepo.findAll()) {
            boolean dirty = false;
            if (c.getCommitteeTerm() == null || c.getCommitteeTerm().isBlank()) {
                c.setCommitteeTerm("第一届");
                dirty = true;
            }
            if (c.getOrgRegion() == null || c.getOrgRegion().isBlank()) {
                c.setOrgRegion("江州市望江区");
                dirty = true;
            }
            if (dirty) {
                communityRepo.save(c);
                log.info("[户口] 小区 {} 补默认届别/区划：{}·{}", c.getName(), c.getOrgRegion(), c.getCommitteeTerm());
            }
        }
    }
}
