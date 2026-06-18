package com.ywh.controller;

import com.ywh.util.Result;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner-meetings")
public class OwnerMeetingController {

    private static final String DISABLED_MESSAGE =
            "业主大会模块已停用：当前产品仅供业委会内部使用，不再采集或处理业主大会数据";

    @RequestMapping({"", "/**"})
    public Result<Void> disabled() {
        return Result.fail(410, DISABLED_MESSAGE);
    }
}
