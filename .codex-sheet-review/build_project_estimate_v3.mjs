import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "outputs/project-budget-estimate";
const outputPath = `${outputDir}/临汾路街道业委会智能履职辅助项目-22.2万元成本平衡版.xlsx`;
const previewDir = ".codex-sheet-review/project-previews-v9";
await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(previewDir, { recursive: true });

const wb = Workbook.create();
const features = wb.worksheets.add("功能与实现路径");
const estimate = wb.worksheets.add("成本预算与周期");

const C = {
  navy: "#1F4E78", blue: "#D9EAF7", pale: "#EEF6FB", green: "#E2F0D9",
  amber: "#FFF2CC", gray: "#F5F7FA", border: "#B7C3D0", dark: "#203040", white: "#FFFFFF",
};
const font = "Microsoft YaHei";

function title(sheet, range, text) {
  sheet.getRange(range).merge();
  const r = sheet.getRange(range);
  r.values = [[text]];
  r.format = {
    fill: C.navy, font: { bold: true, color: C.white, size: 16, name: font },
    horizontalAlignment: "center", verticalAlignment: "center",
  };
  r.format.rowHeight = 34;
}
function section(sheet, range, text) {
  sheet.getRange(range).merge();
  const r = sheet.getRange(range);
  r.values = [[text]];
  r.format = { fill: C.blue, font: { bold: true, color: C.dark, name: font }, verticalAlignment: "center" };
  r.format.rowHeight = 24;
}
function header(r) {
  r.format = {
    fill: C.navy, font: { bold: true, color: C.white, name: font },
    horizontalAlignment: "center", verticalAlignment: "center", wrapText: true,
    borders: { preset: "all", style: "thin", color: C.border },
  };
}
function grid(r) {
  r.format = {
    font: { name: font }, verticalAlignment: "center", wrapText: true,
    borders: { preset: "all", style: "thin", color: C.border },
  };
}

// 第一张：功能清单与实现路径
features.showGridLines = false;
title(features, "A1:F1", "临汾路街道业委会智能履职辅助项目");
features.getRange("A2:F2").merge();
features.getRange("A2:F2").values = [["项目摘要、功能清单与实现路径"]];
features.getRange("A2:F2").format = {
  fill: C.pale, font: { bold: true, color: C.navy, name: font }, horizontalAlignment: "center",
};

features.getRange("A4:B4").merge(); features.getRange("C4:D4").merge(); features.getRange("E4:F4").merge();
features.getRange("A4").values = [["预计实施成本"]];
features.getRange("C4").values = [["建议申报金额"]];
features.getRange("E4").values = [["预计建设周期"]];
features.getRange("A4:F4").format = {
  fill: C.blue, font: { bold: true, color: C.dark, name: font },
  horizontalAlignment: "center", verticalAlignment: "center",
  borders: { preset: "all", style: "thin", color: C.border },
};
features.getRange("A5:B6").merge(); features.getRange("C5:D6").merge(); features.getRange("E5:F6").merge();
features.getRange("A5").formulas = [["='成本预算与周期'!C12"]];
features.getRange("C5").formulas = [["='成本预算与周期'!D12"]];
features.getRange("E5").values = [[6]];
features.getRange("A5:F6").format = {
  font: { bold: true, color: C.navy, name: font, size: 15 },
  horizontalAlignment: "center", verticalAlignment: "center",
  borders: { preset: "all", style: "thin", color: C.border },
};
features.getRange("A5:B6").format.fill = C.pale;
features.getRange("C5:D6").format.fill = C.green;
features.getRange("E5:F6").format.fill = C.amber;
features.getRange("A5:D6").format.numberFormat = '0.00" 万元"';
features.getRange("E5:F6").format.numberFormat = '0.0" 个月"';

section(features, "A8:F8", "一、项目基本情况");
features.getRange("A9:F13").values = [
  ["项目名称", "临汾路街道业委会智能履职辅助项目", null, "建设类型", "新建", null],
  ["申报单位", "上海市静安区人民政府临汾路街道办事处", null, "建设范围", "全街道，实际业委会数量待核实", null],
  ["现状与问题", "会议、接待、学习培训和相关履职资料主要依靠纸质材料、微信群及人工台账，存在期限提醒不足、纪要整理工作量大、佐证材料分散和年度汇总困难。", null, null, null, null],
  ["建设目标", "形成覆盖业委会会议、业主接待、学习培训、材料公示留档、可信存证和提醒待办的统一工作入口，通过AI会议辅助和材料归集提高规范履职效率。", null, null, null, null],
  ["建设特点", "聚焦业委会日常高频履职事项，不追求大而全；会议内容坚持人工确认，会议材料形成公示与留档闭环，接待和培训形成可查询台账，整体操作轻量、易用、可追溯。", null, null, null, null],
];
features.getRange("B9:C9").merge(); features.getRange("E9:F9").merge();
features.getRange("B10:C10").merge(); features.getRange("E10:F10").merge();
features.getRange("B11:F11").merge(); features.getRange("B12:F12").merge(); features.getRange("B13:F13").merge();
grid(features.getRange("A9:F13"));
features.getRange("A9:A13").format = { fill: C.gray, font: { bold: true, name: font },
  verticalAlignment: "center", wrapText: true, borders: { preset: "all", style: "thin", color: C.border } };
features.getRange("D9:D10").format = { fill: C.gray, font: { bold: true, name: font },
  verticalAlignment: "center", wrapText: true, borders: { preset: "all", style: "thin", color: C.border } };

section(features, "A15:F15", "二、功能清单与实现路径");
features.getRange("A16:F16").values = [["序号", "功能模块", "包含的主要功能", "功能实现路径", "形成的材料/结果", "备注"]];
header(features.getRange("A16:F16"));

const featureRows = [
  [1, "业委会会议管理",
    "会议新建、议题及材料准备、通知送达、参会确认、居委会列席记录、签到、表决结果登记、纪要确认、公示和归档。",
    "主任或记录人员发起会议→填写时间地点、议题和参会范围→发送通知并记录送达→线下打印签到/表决签字表，线上由主任登记结果→形成纪要→人工确认后公示归档。",
    "会议通知、送达记录、参会及列席名单、签到表、表决结果、会议纪要、公示材料。",
    "不建设业主大会功能；系统不代替主任作出表决判断。"],
  [2, "AI会议助手",
    "上传会议录音、语音转写、按议题整理讨论内容、生成纪要初稿、提取决议事项和待办任务。",
    "上传录音→调用政府统一提供的大模型及语音能力→生成转写文本和纪要草稿→主任或授权人员校正、审核→确认后进入正式会议纪要和归档流程。",
    "原始录音、转写文本、纪要草稿、决议及待办清单、人工确认记录。",
    "模型资源及调用消耗由政府承担，不计入项目预算。"],
  [3, "业主接待管理",
    "维护接待制度和安排；登记来访事项、诉求内容、附件、处理过程、物业沟通、业主反馈及办结状态。",
    "公布接待安排→接待时建立事项记录→持续补充沟通和处理情况→涉及物业的问题记录物业回复→向业主反馈结果→办结并纳入年度统计。",
    "接待制度、接待台账、物业沟通记录、反馈材料、年度接待统计。",
    "用于形成接待工作过程留痕和评价佐证。"],
  [4, "学习培训管理",
    "登记街道培训、内部学习和专项培训；记录主题、时间、组织单位、参训人员、重点岗位及培训材料。",
    "新建培训或学习记录→选择培训类型→登记参训人员→上传签到表、照片和课件→统计年度学习次数及主任、副主任、印章保管委员参训情况。",
    "培训记录、参训名单、签到表、照片、课件、年度学习统计。",
    "满足学习培训相关评价材料整理需要。"],
  [5, "会议材料公示与留档",
    "归集会议通知、议题材料、签到表、表决结果、会议纪要和公示文件；记录人工确认、公示时间和留档状态，支持查询和下载。",
    "会议办理完成→系统汇总本次会议材料→主任或授权人员核对纪要和公示内容→登记公示时间并上传正式文件→按业委会、届次和年度形成会议材料目录。",
    "会议材料目录、正式纪要、公示文件、签到及表决材料、确认和留档记录。",
    "只聚焦会议材料，不建设覆盖所有业务的综合履职档案平台。"],
  [6, "可信档案馆对接",
    "将人工确认后的正式会议纪要、公示等文件推送至公司现有可信档案馆，并保存存证结果。",
    "正式材料审核确认→调用现有可信档案馆接口→返回存证编号、时间和回执→系统保存回执并支持验真查询；失败时允许重新推送。",
    "存证编号、时间戳、存证回执、验真结果和推送日志。",
    "属于公司已有产品的轻量接口配置，不另行采购平台。"],
  [7, "提醒与待办",
    "提供会议通知时限、公示时限、年度会议次数、接待次数、学习次数、重点人员参训和材料缺失等提醒。",
    "将评价标准转换为可配置规则→系统根据业务日期和完成状态生成待办→临期或逾期时提醒相关人员→处理完成后自动关闭待办并保留记录。",
    "个人待办、临期提醒、逾期提示、材料缺失提示、处理记录。",
    "具体提醒参数可在需求确认阶段由街道最终确定。"],
];
features.getRange("A17:F23").values = featureRows;
grid(features.getRange("A17:F23"));
features.getRange("A17:A23").format.horizontalAlignment = "center";
features.getRange("B17:B23").format = { fill: C.pale, font: { bold: true, color: C.dark, name: font },
  verticalAlignment: "center", wrapText: true, borders: { preset: "all", style: "thin", color: C.border } };
features.getRange("A:A").format.columnWidth = 7;
features.getRange("B:B").format.columnWidth = 19;
features.getRange("C:C").format.columnWidth = 42;
features.getRange("D:D").format.columnWidth = 52;
features.getRange("E:E").format.columnWidth = 35;
features.getRange("F:F").format.columnWidth = 29;
features.getRange("11:13").format.rowHeight = 45;
features.getRange("17:23").format.rowHeight = 76;
features.freezePanes.freezeRows(16);

// 第二张：成本、申报预算和周期
estimate.showGridLines = false;
title(estimate, "A1:G1", "临汾路街道业委会智能履职辅助项目——成本、申报预算与预计周期");
section(estimate, "A3:G3", "一、成本及申报预算（单位：万元）");
estimate.getRange("A4:G4").values = [["费用分类", "测算依据", "预计成本", "申报预算", "差额", "申报占比", "说明"]];
header(estimate.getRange("A4:G4"));
const costRows = [
  ["平台公共能力", "复用组织、权限、附件和消息等公共组件", 2.00, 5.00, null, null, "充分复用现有公共组件，仅计配置、适配和必要改造成本。"],
  ["业务功能开发", "9人月×1.5万元/人月", 13.50, 22.00, null, null, "覆盖7项业务功能；组织用户、权限、附件和基础消息作为公共支撑一并建设。"],
  ["安全合规建设", "身份认证、传输保护、日志和密评整改配合", 1.00, 2.00, null, null, "复用既有安全能力，保留必要配置和密评整改配合。"],
  ["实施与培训", "部署、基础数据初始化、试点上线、手册和培训", 1.20, 3.00, null, null, "以集中部署、一次集中培训和远程支持为主，并保留试点上线投入。"],
  ["第三方测评", "第三方软件测试2万元＋密评暂估0.5万元", 2.50, 3.00, null, null, "密评成本暂按0.5万元估算，最终以采购或合同价格为准。"],
  ["一年运维服务", "故障处理、安全修复、备份检查和小版本升级", 2.00, 5.00, null, null, "覆盖一年基础运行保障，复杂新增需求不计入免费运维。"],
  ["政府承担/不计列", "大模型资源、专用硬件和可信档案馆平台采购", 0.00, 0.00, null, null, "模型消耗由政府承担；不购置专用硬件和可信档案馆平台。"],
];
estimate.getRange("A5:G11").values = costRows;
for (let r = 5; r <= 11; r++) {
  estimate.getRange(`E${r}`).formulas = [[`=D${r}-C${r}`]];
  estimate.getRange(`F${r}`).formulas = [[`=D${r}/$D$12`]];
}
estimate.getRange("A12:B12").merge();
estimate.getRange("A12").values = [["合计"]];
estimate.getRange("C12").formulas = [["=SUM(C5:C11)"]];
estimate.getRange("D12").formulas = [["=SUM(D5:D11)"]];
estimate.getRange("E12").formulas = [["=SUM(E5:E11)"]];
estimate.getRange("F12").formulas = [["=SUM(F5:F11)"]];
estimate.getRange("G12").values = [["成本与申报预算差额包含项目管理、税费、间接成本、交付验收风险、质保服务和合理利润。"]];
grid(estimate.getRange("A5:G12"));
estimate.getRange("A12:G12").format = {
  fill: C.navy, font: { bold: true, color: C.white, name: font },
  borders: { preset: "all", style: "thin", color: C.border }, wrapText: true, verticalAlignment: "center",
};
estimate.getRange("C5:E12").format.numberFormat = "0.00";
estimate.getRange("F5:F12").format.numberFormat = "0.0%";

section(estimate, "A14:G14", "二、预计建设周期：6个月");
estimate.getRange("A15:G15").values = [["阶段", "时间安排", "主要工作", "主要交付物", "实施方式", "阶段成果", "备注"]];
header(estimate.getRange("A15:G15"));
const scheduleRows = [
  ["需求与设计", "第1个月", "确认范围、评价标准映射、业务流程、表单字段、权限和原型。", "需求清单、标准映射表、原型确认稿、详细设计。", "需求确认与基础设计并行推进。", "形成可开发、可验收的范围基线。", "采购准备可同步开展。"],
  ["开发与联调", "第2—3个月", "完成功能开发、公共能力配置、AI能力接入、可信档案馆对接和密码应用建设。", "阶段版本、接口联调记录、数据字典。", "按模块分批开发、分批确认。", "形成可部署试用版本。", "大模型资源由政府提供。"],
  ["部署与试点", "第4个月", "完成环境部署、基础数据初始化、管理员培训和部分业委会试点。", "部署记录、初始化清单、操作手册、培训记录、试点报告。", "选取部分业委会先行试用。", "验证真实业务流程和使用便利性。", "试点问题集中登记。"],
  ["优化与推广", "第5个月", "根据试点反馈优化表单、规则、提醒和操作流程，并逐步推广。", "优化版本、推广记录、问题闭环清单。", "边优化边推广。", "达到全街道推广使用条件。", "实际业委会数量待核实。"],
  ["测评与验收", "第6个月", "开展第三方软件测试、密评、问题整改、资料整理和项目验收。", "测试报告、密评报告、整改记录、验收材料。", "测评准备与前期开发试点适度并行。", "完成项目验收并转入一年运维。", "周期以合同生效或项目启动为起点。"],
];
estimate.getRange("A16:G20").values = scheduleRows;
grid(estimate.getRange("A16:G20"));
estimate.getRange("A16:B20").format.horizontalAlignment = "center";
estimate.getRange("A16:A20").format = { fill: C.pale, font: { bold: true, name: font },
  horizontalAlignment: "center", verticalAlignment: "center", wrapText: true,
  borders: { preset: "all", style: "thin", color: C.border } };

section(estimate, "A22:G22", "三、申报建议");
estimate.getRange("A23:G24").merge();
estimate.getRange("A23:G24").formulas = [[
  `="预计实施成本约"&TEXT(C12,"0.00")&"万元，建议申报金额"&TEXT(D12,"0.00")&"万元，预计建设周期6个月。内部人工按9人月、1.5万元/人月测算；大模型资源及调用消耗由政府承担，不计入本项目申报预算。"`
]];
estimate.getRange("A23:G24").format = {
  fill: C.green, font: { bold: true, color: "#375623", size: 12, name: font },
  horizontalAlignment: "center", verticalAlignment: "center", wrapText: true,
  borders: { preset: "outside", style: "medium", color: "#70AD47" },
};

estimate.getRange("A:A").format.columnWidth = 19;
estimate.getRange("B:B").format.columnWidth = 32;
estimate.getRange("C:F").format.columnWidth = 14;
estimate.getRange("G:G").format.columnWidth = 36;
estimate.getRange("5:11").format.rowHeight = 43;
estimate.getRange("12:12").format.rowHeight = 48;
estimate.getRange("16:20").format.rowHeight = 62;
estimate.freezePanes.freezeRows(4);

for (const range of ["功能与实现路径!A1:F23", "成本预算与周期!A1:G24"]) {
  const res = await wb.inspect({
    kind: "table", range, include: "values,formulas", tableMaxRows: 30, tableMaxCols: 10, maxChars: 18000,
  });
  console.log(res.ndjson);
}
const errors = await wb.inspect({
  kind: "match", searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 }, maxChars: 4000, summary: "formula errors",
});
console.log(errors.ndjson);

for (const [sheetName, range, file] of [
  ["功能与实现路径", "A1:F23", "features.png"],
  ["成本预算与周期", "A1:G24", "estimate.png"],
]) {
  const png = await wb.render({ sheetName, range, scale: 1.1, format: "png" });
  await fs.writeFile(`${previewDir}/${file}`, new Uint8Array(await png.arrayBuffer()));
}

const out = await SpreadsheetFile.exportXlsx(wb);
await out.save(outputPath);
console.log(outputPath);
