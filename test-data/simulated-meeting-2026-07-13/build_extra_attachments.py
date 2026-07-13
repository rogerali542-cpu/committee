from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.pagesizes import A4
from reportlab.lib.colors import HexColor, white

OUT = Path(__file__).resolve().parent
FONT_PATH = Path(r"C:\Windows\Fonts\msyh.ttc")
FONT_BOLD_PATH = Path(r"C:\Windows\Fonts\msyhbd.ttc")

def f(size, bold=False):
    return ImageFont.truetype(str(FONT_BOLD_PATH if bold else FONT_PATH), size)

def header(draw, title, subtitle, width):
    draw.rectangle((0,0,width,150), fill="#173F5F")
    draw.text((55,34), title, font=f(38,True), fill="white")
    draw.text((56,92), subtitle, font=f(20), fill="#D8ECF7")

def save_flood_jpg():
    w,h=1400,900; im=Image.new("RGB",(w,h),"#DCE6EA"); d=ImageDraw.Draw(im)
    header(d,"地下车库防汛物资现场记录","议题一附件｜模拟照片｜2026-07-13",w)
    d.rectangle((65,195,1335,825),fill="#66757F",outline="#34454F",width=6)
    d.polygon([(160,760),(520,330),(880,330),(1240,760)],fill="#4A555B")
    for x in range(240,1160,140): d.line((x,760,x+280,330),fill="#D5D9DC",width=8)
    for i in range(6):
        x=90+i*190; y=690-(i%2)*62
        d.rounded_rectangle((x,y,x+170,y+72),18,fill="#D2A444",outline="#FFF1B8",width=3)
        d.text((x+38,y+20),"防汛沙袋",font=f(21,True),fill="#493500")
    d.rounded_rectangle((1020,215,1280,410),20,fill="#EAF4F8",outline="#1C6E8C",width=5)
    d.text((1062,245),"排水泵",font=f(30,True),fill="#173F5F")
    d.ellipse((1085,305,1215,435),fill="#3198B5",outline="white",width=5)
    d.text((70,842),"说明：用于测试 JPG 上传、预览、下载及通知议题关联。画面为模拟示意。",font=f(22),fill="#34454F")
    im.save(OUT/"04-防汛物资现场记录.jpg",quality=92)

def save_playground_jpg():
    w,h=1400,900; im=Image.new("RGB",(w,h),"#DBF0D4"); d=ImageDraw.Draw(im)
    header(d,"儿童活动区开放时段讨论现场","议题二附件｜模拟照片｜2026-07-13",w)
    d.rectangle((0,150,w,h),fill="#B9DFAF"); d.rectangle((0,650,w,h),fill="#76B46A")
    d.rectangle((110,250,1290,680),fill="#E8C48B",outline="#B07C39",width=6)
    d.rectangle((270,330,320,600),fill="#E3514C"); d.rectangle((520,330,570,600),fill="#E3514C")
    d.rectangle((250,310,590,350),fill="#F4C542"); d.line((320,350,395,520),fill="#2176AE",width=22); d.line((540,350,465,520),fill="#2176AE",width=22)
    d.ellipse((370,495,490,615),fill="#42A5F5")
    d.rounded_rectangle((760,250,1190,565),25,fill="#FFF9E8",outline="#D29A28",width=5)
    d.text((815,285),"拟议开放时段",font=f(32,True),fill="#8A5A00")
    d.text((810,360),"工作日  08:30-20:30",font=f(25),fill="#493B20")
    d.text((810,415),"周末    09:00-20:30",font=f(25),fill="#493B20")
    d.text((810,485),"午休 12:00-14:30",font=f(25,True),fill="#B3483A")
    d.text((70,842),"说明：用于测试 JPG 图片材料与讨论议题关联。画面为模拟示意。",font=f(22),fill="#31582B")
    im.save(OUT/"05-儿童活动区讨论现场.jpg",quality=92)

def save_schedule_png():
    w,h=1400,900; im=Image.new("RGB",(w,h),"white"); d=ImageDraw.Draw(im)
    header(d,"汛期双人值守排班示意","议题一附件｜PNG透明/清晰文字预览测试",w)
    cols=[80,290,600,910,1220]; rows=[205,295,385,475,565,655,745]
    labels=[("日期","时段","值守A","值守B"),("7月16日","19:00-23:00","张师傅","刘师傅"),("7月17日","19:00-23:00","王师傅","赵师傅"),("7月18日","19:00-23:00","刘师傅","陈师傅"),("7月19日","19:00-23:00","张师傅","王师傅"),("触发条件","橙色及以上暴雨预警","每2小时巡查","异常立即上报")]
    for r in range(6):
        fill="#E7F2F8" if r==0 else ("#F7FAFC" if r%2 else "white")
        d.rectangle((cols[0],rows[r],cols[-1],rows[r+1]),fill=fill,outline="#8CA6B6",width=2)
        for c in cols[1:-1]: d.line((c,rows[r],c,rows[r+1]),fill="#8CA6B6",width=2)
        for c,text in enumerate(labels[r]): d.text((cols[c]+20,rows[r]+25),text,font=f(21,r==0),fill="#173F5F")
    d.text((82,790),"注：姓名及排班均为虚构测试数据。",font=f(20),fill="#666666")
    im.save(OUT/"06-防汛值守排班示意.png")

def save_quote_png():
    w,h=1400,1000; im=Image.new("RGB",(w,h),"#F7F8FA"); d=ImageDraw.Draw(im)
    header(d,"公共区域照明节能改造报价对比","议题三附件｜PNG报价截图模拟",w)
    cards=[("甲方供应商","17,680元","质保24个月","预计节电28%","#E8F2FA"),("乙方供应商","18,000元","质保36个月","预计节电25%","#FFF2D9"),("丙方供应商","16,900元","质保12个月","预计节电22%","#EAF6E7")]
    for i,(name,price,warranty,saving,color) in enumerate(cards):
        x=80+i*440; d.rounded_rectangle((x,220,x+390,770),25,fill=color,outline="#7392A5",width=4)
        d.text((x+42,265),name,font=f(30,True),fill="#173F5F")
        d.text((x+42,355),price,font=f(42,True),fill="#B34A32")
        d.line((x+40,430,x+350,430),fill="#AFC1CB",width=2)
        d.text((x+42,475),warranty,font=f(24),fill="#34454F")
        d.text((x+42,545),saving,font=f(24),fill="#34454F")
        d.text((x+42,650),"模拟报价",font=f(22,True),fill="#777777")
    d.text((80,845),"测试结论：本次表决预算上限18,000元；报价仅用于功能测试，不构成采购依据。",font=f(24,True),fill="#173F5F")
    im.save(OUT/"07-照明改造报价对比.png")

def register_pdf_fonts():
    pdfmetrics.registerFont(TTFont("CN", str(FONT_PATH), subfontIndex=0))
    pdfmetrics.registerFont(TTFont("CN-B", str(FONT_BOLD_PATH), subfontIndex=0))

def pdf_header(c,title,subtitle,page):
    W,H=A4; c.setFillColor(white); c.rect(0,0,W,H,fill=1,stroke=0)
    c.setFillColor(HexColor("#173F5F")); c.rect(0,H-110,W,110,fill=1,stroke=0)
    c.setFillColor(white); c.setFont("CN-B",20); c.drawString(46,H-55,title)
    c.setFont("CN",10); c.drawString(46,H-82,subtitle); c.setFillColor(HexColor("#777777")); c.drawRightString(W-42,28,f"第 {page} 页")

def line(c,text,y,size=11,bold=False,color="#222222"):
    c.setFont("CN-B" if bold else "CN",size); c.setFillColor(HexColor(color)); c.drawString(48,y,text)

def save_plan_pdf():
    register_pdf_fonts(); path=OUT/"08-照明节能改造试点方案.pdf"; c=canvas.Canvas(str(path),pagesize=A4); W,H=A4
    pdf_header(c,"公共区域照明节能改造试点方案","议题三正式附件｜模拟测试数据",1)
    y=H-155; line(c,"一、项目范围",y,15,True,"#173F5F"); y-=35
    for t in ["1号楼地下车库40盏、一层公共走廊20盏，共60盏。","采用LED灯具及人体感应控制，保留手动常亮回退能力。"]: line(c,t,y); y-=27
    line(c,"二、预算与付款",y-10,15,True,"#173F5F"); y-=55
    for t in ["预算上限：人民币18,000元（含设备、安装与调试）。","验收合格后支付90%，质保满3个月支付剩余10%。"]: line(c,t,y); y-=27
    line(c,"三、验收指标",y-10,15,True,"#173F5F"); y-=55
    for t in ["照度不低于改造前；试运行期故障率不高于2%。","节电率目标不低于25%；连续两周投诉且整改无效时回退。"]: line(c,t,y); y-=27
    c.setFillColor(HexColor("#FFF2D9")); c.roundRect(45,y-95,W-90,80,8,fill=1,stroke=0)
    line(c,"表决选项：赞成 / 反对 / 弃权",y-47,13,True,"#8A5A00"); line(c,"弃权须单独计入已表决人数，不得归入未表决。",y-75,11,False,"#8A5A00")
    c.showPage(); pdf_header(c,"公共区域照明节能改造试点方案","附件：模拟验收记录表",2)
    y=H-165; rows=[("检查项","验收标准","模拟结果"),("灯具数量","共60盏安装完成","通过"),("平均照度","不低于改造前","通过"),("故障率","不高于2%","1.7%"),("节电率","目标不低于25%","27%"),("回退能力","可切换常亮模式","通过")]
    xs=[48,190,425,548]; rh=50
    for r,row in enumerate(rows):
        yy=y-r*rh; c.setFillColor(HexColor("#E7F2F8" if r==0 else "#FFFFFF")); c.rect(xs[0],yy-rh,xs[-1]-xs[0],rh,fill=1,stroke=1)
        for x in xs[1:-1]: c.line(x,yy-rh,x,yy)
        for i,t in enumerate(row): c.setFont("CN-B" if r==0 else "CN",10); c.setFillColor(HexColor("#173F5F" if r==0 else "#222222")); c.drawString(xs[i]+8,yy-30,t)
    c.save()

def save_minutes_pdf():
    path=OUT/"09-儿童活动区意见汇总.pdf"; c=canvas.Canvas(str(path),pagesize=A4); W,H=A4
    pdf_header(c,"儿童活动区开放时段意见汇总","议题二讨论附件｜模拟居民意见",1)
    y=H-160; line(c,"汇总说明",y,15,True,"#173F5F"); y-=34
    line(c,"共收集模拟意见12条：支持方案6条、建议缩短晚间时段4条、其他2条。",y); y-=45
    items=[("意见A","建议工作日20:00后停止高噪声活动。"),("意见B","暑假期间可延长至20:30，但需加强巡查。"),("意见C","午休12:00至14:30应保持关闭。"),("意见D","建议在东、西入口同时设置开放时间告示牌。")]
    for title,text in items:
        c.setFillColor(HexColor("#F2F6F8")); c.roundRect(48,y-62,W-96,55,6,fill=1,stroke=0)
        line(c,title,y-30,11,True,"#173F5F"); line(c,text,y-51,10); y-=78
    line(c,"建议形成的讨论意见",y-5,15,True,"#173F5F"); y-=42
    for t in ["试行30天，每周汇总投诉数量及平均处理时长。","工作日与周末分别设置开放时段，午休时段暂停开放。","下次会议根据试行数据决定是否调整；本次不进行表决。"]: line(c,"• "+t,y); y-=29
    c.save()

if __name__ == "__main__":
    save_flood_jpg(); save_playground_jpg(); save_schedule_png(); save_quote_png(); save_plan_pdf(); save_minutes_pdf()
