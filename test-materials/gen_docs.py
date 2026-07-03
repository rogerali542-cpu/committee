# -*- coding: utf-8 -*-
"""
生成业委会 App 测试用文档图片：
  - 会议通知（拍照/上传 → OCR 判类为 notice → 自动预填新建会议）
  - 会议材料（拍照/上传 → OCR 判类为 material → 传阅并喂 AI 纪要）
  - 纸质拍照风格（模拟手机拍纸质文件：米色背景 + 轻微旋转 + 阴影）
生成物按场景归到 会议通知/ 会议材料/ 纸质拍照/ 三个子目录。
用法：python gen_docs.py
"""
import os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
F_REG = "C:/Windows/Fonts/msyh.ttc"    # 微软雅黑
F_BLD = "C:/Windows/Fonts/msyhbd.ttc"  # 微软雅黑粗
F_SUN = "C:/Windows/Fonts/simsun.ttc"  # 宋体（换个字形，模拟不同来源）

W = 1000
MARGIN = 70
INK = (34, 34, 34)
GREY = (90, 90, 90)
ACCENT = (198, 120, 0)  # 橙


def font(path, size):
    return ImageFont.truetype(path, size)


def wrap(draw, text, fnt, max_w):
    """按像素宽度对中文断行"""
    lines, cur = [], ""
    for ch in text:
        if ch == "\n":
            lines.append(cur); cur = ""; continue
        if draw.textlength(cur + ch, font=fnt) <= max_w:
            cur += ch
        else:
            lines.append(cur); cur = ch
    if cur:
        lines.append(cur)
    return lines


def new_canvas(h, bg=(255, 255, 255)):
    img = Image.new("RGB", (W, h), bg)
    return img, ImageDraw.Draw(img)


def draw_block(draw, x, y, text, fnt, fill, max_w, lh=1.5):
    for ln in wrap(draw, text, fnt, max_w):
        draw.text((x, y), ln, font=fnt, fill=fill)
        y += int(fnt.size * lh)
    return y


def render_notice(fname, title, fields, footer):
    """会议通知：橙色标题带 + 字段行"""
    img, d = new_canvas(1400)
    f_title = font(F_BLD, 46)
    f_key = font(F_BLD, 34)
    f_val = font(F_REG, 34)
    f_foot = font(F_REG, 28)
    # 顶部橙带
    d.rectangle([0, 0, W, 14], fill=ACCENT)
    y = 60
    # 标题居中
    tw = d.textlength(title, font=f_title)
    d.text(((W - tw) / 2, y), title, font=f_title, fill=INK)
    y += 90
    d.line([MARGIN, y, W - MARGIN, y], fill=(220, 220, 220), width=2)
    y += 40
    for k, v in fields:
        d.text((MARGIN, y), k, font=f_key, fill=ACCENT)
        kx = MARGIN + d.textlength(k, font=f_key) + 16
        y2 = draw_block(d, kx, y, v, f_val, INK, W - MARGIN - kx, lh=1.45)
        y = max(y + int(f_key.size * 1.45), y2) + 18
    y += 20
    d.line([MARGIN, y, W - MARGIN, y], fill=(220, 220, 220), width=2)
    y += 30
    y = draw_block(d, MARGIN, y, footer, f_foot, GREY, W - 2 * MARGIN, lh=1.6)
    img = img.crop((0, 0, W, min(1400, y + 60)))
    img.save(fname)
    print("notice ->", os.path.relpath(fname, BASE))


def render_material(fname, title, subtitle, paragraphs, font_reg=F_REG, font_bld=F_BLD):
    """会议材料：方案/报告体，标题 + 若干带小标题的段落"""
    img, d = new_canvas(2000)
    f_title = font(font_bld, 44)
    f_sub = font(font_reg, 30)
    f_h = font(font_bld, 34)
    f_body = font(font_reg, 31)
    y = 64
    tw = d.textlength(title, font=f_title)
    d.text(((W - tw) / 2, y), title, font=f_title, fill=INK)
    y += 74
    if subtitle:
        sw = d.textlength(subtitle, font=f_sub)
        d.text(((W - sw) / 2, y), subtitle, font=f_sub, fill=GREY)
        y += 56
    d.line([MARGIN, y, W - MARGIN, y], fill=(210, 210, 210), width=2)
    y += 36
    for h, body in paragraphs:
        if h:
            d.text((MARGIN, y), h, font=f_h, fill=ACCENT)
            y += int(f_h.size * 1.5)
        y = draw_block(d, MARGIN, y, body, f_body, INK, W - 2 * MARGIN, lh=1.62)
        y += 26
    img = img.crop((0, 0, W, min(2000, y + 50)))
    img.save(fname)
    print("material ->", os.path.relpath(fname, BASE))


def to_photo(src, dst, angle=-3.2, bg=(232, 228, 218)):
    """把干净文档转成"手机拍纸质文件"风格：米色背景 + 轻微旋转 + 阴影"""
    doc = Image.open(src).convert("RGB")
    # 缩到合适大小
    scale = 900 / doc.width
    doc = doc.resize((int(doc.width * scale), int(doc.height * scale)))
    canvas = Image.new("RGB", (doc.width + 260, doc.height + 260), bg)
    # 阴影
    shadow = Image.new("RGB", doc.size, (60, 58, 52))
    sh = shadow.rotate(angle, expand=True)
    canvas.paste(sh, (140, 150))
    pg = doc.rotate(angle, expand=True, fillcolor=(255, 255, 255))
    canvas.paste(pg, (120, 120))
    canvas.save(dst)
    print("photo ->", os.path.relpath(dst, BASE))


def main():
    d_notice = os.path.join(BASE, "会议通知")
    d_mat = os.path.join(BASE, "会议材料")
    d_photo = os.path.join(BASE, "纸质拍照")
    for p in (d_notice, d_mat, d_photo):
        os.makedirs(p, exist_ok=True)

    # ── 会议通知（对应 4 个测试会议 A/B/C/D）──
    render_notice(
        os.path.join(d_notice, "通知A_电梯与监控_有表决.png"),
        "阳光花园小区业主委员会会议通知",
        [
            ("会议名称：", "2026年第一次业主委员会例会"),
            ("会议时间：", "2026年7月10日（周五）上午9:30"),
            ("会议地点：", "小区党群服务中心 二楼会议室"),
            ("参会人员：", "全体业主委员会委员"),
            ("审议议题：", "1. 审议《小区电梯年度维保方案》（需表决）\n"
                       "2. 审议《公共区域监控系统升级预算》（需表决）"),
        ],
        "请各位委员准时参会。如有特殊情况不能到会，请提前向业委会主任请假。",
    )
    render_notice(
        os.path.join(d_notice, "通知B_物业费调整.png"),
        "业主委员会专题会议通知",
        [
            ("会议名称：", "物业服务费调整专题讨论会"),
            ("会议时间：", "2026年7月12日（周日）下午14:00"),
            ("会议地点：", "小区物业办公室 会议室"),
            ("审议议题：", "1. 审议《物业服务费调整方案》（需表决）"),
        ],
        "本次会议就物业费调整方案进行讨论。相关材料已随通知一并送达，请提前审阅。",
    )
    render_notice(
        os.path.join(d_notice, "通知C_季度通报.png"),
        "业主委员会季度工作通报会通知",
        [
            ("会议名称：", "2026年第二季度工作通报会"),
            ("会议时间：", "2026年7月15日（周三）上午10:00"),
            ("会议地点：", "小区党群服务中心 二楼会议室"),
            ("通报事项：", "1. 通报上一季度财务收支情况\n2. 通报小区绿化改造工程进展"),
        ],
        "本次会议为情况通报，不涉及表决事项，欢迎各位委员到会了解。",
    )
    render_notice(
        os.path.join(d_notice, "通知D_电梯与充电桩_混合.png"),
        "业主委员会会议通知",
        [
            ("会议名称：", "2026年7月安全设施专题会"),
            ("会议时间：", "2026年7月18日（周五）上午9:00"),
            ("会议地点：", "小区党群服务中心 二楼会议室"),
            ("审议议题：", "1. 审议《小区电梯年度维保方案》（需表决）\n"
                       "2. 审议《非机动车充电桩加装方案》（需表决）"),
        ],
        "两项议题均需现场表决，请全体委员务必到会。",
    )

    # ── 会议材料（传阅 / 喂 AI 纪要）──
    render_material(
        os.path.join(d_mat, "材料_电梯年度维保方案.png"),
        "小区电梯年度维保方案",
        "阳光花园小区业主委员会 · 2026年7月",
        [
            ("一、现状", "小区现有客梯 12 部，投入使用已满 6 年，近半年故障报修 23 次，"
                       "其中困人 2 次。现有维保单位合同将于本月到期。"),
            ("二、方案要点", "1. 拟续聘具备 A 级资质的维保单位，维保周期由每月 1 次提高到每半月 1 次；"
                         "2. 年度维保总费用 18.6 万元，较上一年度增加 2.4 万元；"
                         "3. 增加电梯物联网监测终端，实现困人自动报警。"),
            ("三、经费来源", "从小区公共维修资金及物业公共收益中列支，不再另行向业主分摊。"),
            ("四、建议", "提请本次业委会会议审议表决。"),
        ],
    )
    render_material(
        os.path.join(d_mat, "材料_物业服务费调整方案.png"),
        "物业服务费调整方案",
        "阳光花园小区业主委员会 · 2026年7月",
        [
            ("一、调整背景", "现行物业服务费标准为每月每平方米 1.20 元，已执行 5 年。"
                         "人工、保洁、绿化等成本逐年上升，现有收费已难以覆盖服务成本。"),
            ("二、调整方案", "拟自 2026 年 10 月起，物业服务费调整为每月每平方米 1.45 元，"
                         "涨幅 0.25 元。多层住宅、高层住宅执行同一标准。"),
            ("三、服务承诺", "调整后物业增加：24 小时安保巡逻、每周两次公共区域深度保洁、"
                         "增设 2 名绿化养护人员。"),
            ("四、征询意见", "本方案提请业委会审议表决，并将结果在小区公示栏公示 7 天。"),
        ],
        font_reg=F_SUN, font_bld=F_BLD,
    )
    render_material(
        os.path.join(d_mat, "材料_非机动车充电桩加装方案.png"),
        "非机动车充电桩加装方案",
        "阳光花园小区业主委员会 · 2026年7月",
        [
            ("一、需求", "小区电动自行车约 600 辆，现有充电点位仅 40 个，飞线充电、"
                       "楼道停放隐患突出，居民反映强烈。"),
            ("二、方案", "拟在 3 处非机动车棚新增智能充电桩共 120 个接口，"
                       "配备扫码计费与过载保护、烟感联动断电。"),
            ("三、投资与运营", "建设投资约 9.8 万元，由第三方运营商投资建设，"
                           "业委会不承担建设费用，按充电量分成。"),
            ("四、建议", "提请本次会议审议表决。"),
        ],
    )
    render_material(
        os.path.join(d_mat, "材料_季度财务收支报告.png"),
        "第二季度财务收支情况通报",
        "阳光花园小区业主委员会 · 2026年7月",
        [
            ("一、收入", "本季度公共收益合计 12.8 万元：其中广告位收入 6.2 万元、"
                       "地面停车收入 4.9 万元、场地租赁 1.7 万元。"),
            ("二、支出", "本季度支出合计 7.3 万元：公共设施维修 3.1 万元、"
                       "绿化养护 1.8 万元、公共能耗 1.6 万元、办公及其他 0.8 万元。"),
            ("三、结余", "本季度结余 5.5 万元，累计公共收益结余 41.2 万元，"
                       "已全额存入业委会共管账户。"),
            ("四、说明", "本报告为情况通报，明细账目可在业委会办公室查阅。"),
        ],
    )

    # ── 纸质拍照风格（挑两份转成"手机拍纸"）──
    to_photo(os.path.join(d_notice, "通知A_电梯与监控_有表决.png"),
             os.path.join(d_photo, "拍照_通知A_电梯与监控.png"), angle=-3.5)
    to_photo(os.path.join(d_mat, "材料_电梯年度维保方案.png"),
             os.path.join(d_photo, "拍照_材料_电梯维保方案.png"), angle=2.6,
             bg=(224, 220, 210))


if __name__ == "__main__":
    main()
