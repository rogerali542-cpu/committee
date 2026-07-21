from copy import deepcopy
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Pt


SOURCE = Path(r"C:\Users\Lequan\Desktop\20250829_2026年度静安区信息化建设项目预算申报表.docx")
OUTPUT = Path(r"C:\Users\Lequan\Documents\WXWork\1688855966854643\Cache\File\2026-06\new-app\new-app\2027年度临汾路街道业委会智能履职辅助项目预算申报表-原格式重写版.docx")


def set_run_font(run, size=None, bold=None):
    run.font.name = "仿宋_GB2312"
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), "仿宋_GB2312")
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold


def set_cell(cell, text, size=10.5, bold=False, center=False):
    p = cell.paragraphs[0]
    old_rpr = None
    if p.runs and p.runs[0]._r.rPr is not None:
        old_rpr = deepcopy(p.runs[0]._r.rPr)
    for extra_p in cell.paragraphs[1:]:
        cell._tc.remove(extra_p._p)
    old_ppr = deepcopy(p._p.pPr) if p._p.pPr is not None else None
    for child in list(p._p):
        if child.tag != qn("w:pPr"):
            p._p.remove(child)
    if old_ppr is not None:
        if p._p.pPr is not None:
            p._p.remove(p._p.pPr)
        p._p.insert(0, old_ppr)
    if center:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text)
    if old_rpr is not None:
        if r._r.rPr is not None:
            r._r.remove(r._r.rPr)
        r._r.insert(0, old_rpr)
    if bold:
        r.bold = True


def unique_cells(row):
    result, seen = [], set()
    for cell in row.cells:
        key = id(cell._tc)
        if key not in seen:
            seen.add(key)
            result.append(cell)
    return result


def fill_unique_row(table, row_index, values, size=9, bold=False):
    cells = unique_cells(table.rows[row_index])
    if len(cells) != len(values):
        raise ValueError(f"row {row_index}: expected {len(cells)} values, got {len(values)}")
    for i, (cell, value) in enumerate(zip(cells, values)):
        set_cell(cell, str(value), size=size, bold=bold, center=(i == 0 or len(str(value)) <= 8))


doc = Document(SOURCE)

# Title: preserve the original title formatting and only replace its text.
title = doc.paragraphs[0]
if title.runs:
    title.runs[0].text = "2027年度静安区信息化建设项目预算申报表"
    for extra in title.runs[1:]:
        extra.text = ""
else:
    title.add_run("2027年度静安区信息化建设项目预算申报表")

# 一、基本情况
t0 = doc.tables[0]
fill_unique_row(t0, 0, ["预算主管部门\n（申报单位）", "上海市静安区人民政府临汾路街道办事处"], 10.5)
fill_unique_row(t0, 1, ["预算部门\n（建设单位）", "上海市静安区人民政府临汾路街道办事处"], 10.5)
fill_unique_row(t0, 2, ["项目名称", "临汾路街道业委会智能履职辅助项目", "☑新建  □升级改造"], 10.5)
fill_unique_row(t0, 3, ["项目负责人", "待确认", "手机", "待确认"], 10.5)
fill_unique_row(t0, 4, ["申报金额", "42.50（万元）"], 11, True)
fill_unique_row(t0, 5, ["是否下沉街镇", "是", "是否下沉居村", "是", "是否是信创改造", "否"], 9.5)
summary = (
    "建设面向临汾路街道辖区业主委员会的智能履职辅助应用，围绕会议、业主接待、学习培训、"
    "材料公示留档和提醒待办，提供流程记录、语音转写、纪要初稿、材料归集及可信存证。"
    "项目预计实施成本22.20万元，申报金额42.50万元，建设周期6个月；大模型资源及调用消耗由政府承担。"
)
fill_unique_row(t0, 6, [f"建设目的及主要建设内容（200字以内）\n{summary}"], 9.5)

# 二、现状
t1 = doc.tables[1]
fill_unique_row(t1, 0, ["建\n设\n情\n况", "本项目为新建项目，本栏不适用。"], 10.5)
fill_unique_row(t1, 1, ["运\n行\n情\n况", "无现有正式立项建设的信息系统。前期仅开展业务需求梳理和应用原型验证。"], 10.5)
fill_unique_row(t1, 2, ["运\n行\n情\n况", "第三方评估报告：□有  ☑无"], 10.5)

# 四、需求分析
t3 = doc.tables[3]
needs = (
    "现有信息系统与实际业务需求之间的差距：\n"
    "目前辖区业主委员会的会议通知、议题材料、签到表、表决结果、会议纪要、业主接待记录和学习培训材料，"
    "主要依靠纸质文件、微信群和人工台账管理，存在信息分散、期限提醒不足、记录要素不完整、材料查询困难和年度汇总工作量大等问题。"
    "会议录音整理和纪要编写耗时较长，正式公示材料也缺少统一的可信存证回执。"
    "因此需要建设统一、轻量、可追溯的履职辅助应用，对重点工作形成流程记录、智能提醒、人工确认和电子留档闭环。"
)
fill_unique_row(t3, 0, [needs], 10.5)

# 五、建设方案
t4 = doc.tables[4]
functions = (
    "主要需要实现的模块及主要功能：（特别提示：需在附件中另附建设方案，见附件信息化项目建设方案编制大纲）\n"
    "1. 业委会会议管理：支持会议新建、议题及材料准备、通知送达、参会确认、居委会列席记录、签到、"
    "表决结果登记、纪要确认、公示和归档。线下会议打印签到或表决签字表，线上会议由主任把握程序并填写投票结果。\n"
    "2. AI会议助手：上传会议录音，调用政府提供的语音及大模型能力完成转写、按议题整理讨论内容、生成纪要初稿，"
    "提取决议和待办；所有正式内容均由主任或授权人员人工校正确认。\n"
    "3. 业主接待管理：登记接待安排、来访事项、诉求内容、附件、处理过程、物业沟通、业主反馈及办结状态，形成年度接待台账。\n"
    "4. 学习培训管理：登记内部学习、街镇培训和专项培训，记录主题、时间、组织单位、实际参训人员及岗位，"
    "上传签到表、照片和课件，统计年度学习次数及重点岗位人员参训情况。\n"
    "5. 会议材料公示与留档：归集会议通知、议题材料、签到表、表决结果、会议纪要和公示文件，记录人工确认、公示时间和留档状态，"
    "按业委会、届次和年度形成可查询、可下载的会议材料目录。\n"
    "6. 可信档案馆对接：将人工确认后的正式会议纪要和公示文件推送至街道已使用的可信档案馆，保存存证编号、时间、回执和验真结果。\n"
    "7. 提醒与待办：针对会议通知、公示、年度会议及学习次数、重点人员参训和材料缺失等事项生成临期、逾期和补充材料提醒。"
)
fill_unique_row(t4, 0, [functions], 9)

# Hardware rental
fill_unique_row(t4, 1, ["需要租赁的硬件："], 9.5, True)
fill_unique_row(t4, 2, ["序号", "名称", "配置要求", "单价\n（万元）", "数量", "总价\n（万元）"], 8.5, True)
fill_unique_row(t4, 3, ["1", "无", "本期不租赁专用硬件", "0", "0", "0"], 9)
fill_unique_row(t4, 4, ["租赁硬件费用合计", "0"], 9.5, True)

# Product software purchase
fill_unique_row(t4, 5, ["需要购置的产品软件(需提供软件产品登记证书)："], 9.5, True)
fill_unique_row(t4, 6, ["序号", "名称", "功能描述", "单价\n（万元）", "数量", "总价\n（万元）"], 8.5, True)
product_rows = [
    ["1", "无", "本项目充分复用现有组件，不另行购置产品软件", "0", "0", "0"],
    ["2", "大模型资源服务", "由政府统一提供并承担调用消耗，本项目不重复计费", "0", "1", "0"],
    ["3", "可信档案馆平台", "复用公司现有产品，仅实施轻量接口对接", "0", "1", "0"],
    ["4", "无", "", "0", "0", "0"],
    ["5", "无", "", "0", "0", "0"],
    ["6", "无", "", "0", "0", "0"],
]
for row_idx, values in enumerate(product_rows, 7):
    fill_unique_row(t4, row_idx, values, 8.5)
fill_unique_row(t4, 13, ["购置产品软件费用合计", "0"], 9.5, True)

# Application development budget: 27.50 = public platform 5.00 + business development 22.50
fill_unique_row(t4, 14, ["应用软件开发："], 9.5, True)
fill_unique_row(t4, 15, ["序号", "一级模块", "二级模块", "功能描述", "单价\n(万元)", "人月/项", "金额\n（万元）"], 8.3, True)
dev_rows = [
    ["1", "平台公共能力", "公共组件适配", "复用组织、权限、附件和消息组件，完成配置及必要改造", "5.00", "1", "5.00"],
    ["2", "会议管理", "会议履职流程", "会议、通知、签到、表决、纪要、公示及材料留档", "6.00", "1", "6.00"],
    ["3", "AI会议助手", "转写与纪要", "录音转写、议题整理、纪要初稿、决议和待办提取", "4.00", "1", "4.00"],
    ["4", "业主接待", "接待闭环", "接待登记、物业沟通、业主反馈、办结及年度统计", "3.00", "1", "3.00"],
    ["5", "学习培训", "培训台账", "学习培训登记、参训岗位、附件和年度完成情况统计", "2.00", "1", "2.00"],
    ["6", "公示与留档", "会议材料目录", "正式材料确认、公示时间、分类留档、查询及下载", "2.50", "1", "2.50"],
    ["7", "可信存证", "档案馆接口", "正式文件推送、回执保存、失败重推和验真查询", "1.00", "1", "1.00"],
    ["8", "提醒与待办", "规则提醒", "临期、逾期、年度次数及材料缺失提醒", "4.00", "1", "4.00"],
    ["9", "基础支撑", "组织权限附件", "纳入平台公共能力统一实施，本行不重复计费", "0", "0", "0"],
]
for row_idx, values in enumerate(dev_rows, 16):
    fill_unique_row(t4, row_idx, values, 8.2)
fill_unique_row(t4, 25, ["软件开发费用合计", "27.50"], 9.5, True)

# Password application / security
fill_unique_row(t4, 26, ["密码功能模块："], 9.5, True)
fill_unique_row(t4, 27, ["序号", "主要功能模块", "单价\n(万元)", "人月/项", "金额\n（万元）"], 8.5, True)
fill_unique_row(t4, 28, ["1", "身份认证、传输保护、日志配置及密评整改配合", "2.00", "1", "2.00"], 8.8)
fill_unique_row(t4, 29, ["密码功能模块费用合计", "2.00"], 9.5, True)

# Other costs: 13.00
fill_unique_row(t4, 30, ["其他费用："], 9.5, True)
fill_unique_row(t4, 31, ["序号", "科目名称", "金额\n（万元）"], 8.5, True)
other_rows = [
    ["1", "实施与培训（部署、初始化、试点、手册和集中培训）", "3.00"],
    ["2", "第三方测评（软件测试及密码应用测评）", "5.00"],
    ["3", "一年运行维护服务", "5.00"],
    ["4", "大模型资源及调用消耗（政府承担）", "0"],
    ["5", "专用硬件及可信档案馆平台采购", "0"],
    ["6", "其他", "0"],
]
for row_idx, values in enumerate(other_rows, 32):
    fill_unique_row(t4, row_idx, values, 8.8)
fill_unique_row(t4, 38, ["其他费用合计", "13.00"], 9.5, True)
fill_unique_row(t4, 39, ["项目总费用", "42.50"], 10.5, True)

# 六、建设周期
t5 = doc.tables[5]
period = (
    "项目建设的关键节点（图或者文字）\n"
    "项目建设周期预计为6个月。\n"
    "1、第1个月：完成需求范围、评价标准映射、业务流程、表单字段、权限和原型确认。\n"
    "2、第2—3个月：完成功能开发、公共能力配置、AI能力接入、可信档案馆接口联调和密码应用建设。\n"
    "3、第4个月：完成环境部署、基础数据初始化、管理员培训和部分业委会试点。\n"
    "4、第5个月：根据试点反馈优化表单、规则、提醒和操作流程，逐步推广至全街道。\n"
    "5、第6个月：开展第三方软件测试、密码应用测评、问题整改、资料整理和项目验收，验收后转入一年运维。"
)
fill_unique_row(t5, 0, [period], 10.5)

doc.core_properties.title = "2027年度静安区信息化建设项目预算申报表"
doc.core_properties.subject = "临汾路街道业委会智能履职辅助项目"
doc.core_properties.author = "上海市静安区人民政府临汾路街道办事处"
doc.save(OUTPUT)
print(OUTPUT)
