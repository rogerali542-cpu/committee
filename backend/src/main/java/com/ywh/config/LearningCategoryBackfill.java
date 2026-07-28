package com.ywh.config;

import com.ywh.entity.LearningRecord;
import com.ywh.enums.LearningCategory;
import com.ywh.enums.LearningType;
import com.ywh.repository.LearningRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Flyway 关闭、用 ddl-auto 时，新增的 category 列对存量学习记录为 NULL。
 * 这里在启动时按原 type 一次性回填分类：internal→内部学习，street/special→外部培训。
 * 之后可在详情页显式改分类。幂等：只处理 category 为空的行。
 */
@Component
@Order(50)
@Slf4j
@RequiredArgsConstructor
public class LearningCategoryBackfill implements CommandLineRunner {

    private final LearningRecordRepository repo;

    @Override
    public void run(String... args) {
        List<LearningRecord> pending = new ArrayList<>();
        for (LearningRecord r : repo.findAll()) {
            if (r.getCategory() == null) {
                r.setCategory(r.getType() == LearningType.internal
                        ? LearningCategory.internal
                        : LearningCategory.external);
                pending.add(r);
            }
        }
        if (!pending.isEmpty()) {
            repo.saveAll(pending);
            log.info("[LearningCategoryBackfill] 已按 type 回填 {} 条学习记录的分类", pending.size());
        }
    }
}
