package com.ywh.util;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * PDF 中文字体统一加载（0723）：此前三个 PDF 服务各写死 4 条字体路径，
 * 机器上没有这几个文件（如字体装在别的目录、精简版系统）导出就直接报
 * 「服务器缺少中文字体」。统一为三级查找：
 *   1) 显式指定：JVM 参数 -Dywh.pdf.font=/path/to/font.ttf 或环境变量 YWH_PDF_FONT；
 *   2) 各平台常见的中文字体路径（Windows/Linux/macOS）；
 *   3) 扫描系统字体目录，按文件名匹配常见中文字体（黑体/雅黑/宋体/思源/Noto/文泉驿/苹方等）。
 * 找到的路径静态缓存，后续导出不再重复扫盘（PDFont 本身绑定单个文档，不能跨文档缓存）。
 */
public final class PdfFontLoader {

    private PdfFontLoader() {}

    private static volatile File cachedFontFile; // 已验证可加载的字体文件（跨请求复用路径，不复用 PDFont）

    private static final String[] FIXED_CANDIDATES = {
            // Windows
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/msyh.ttf",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/simsunb.ttf",
            "C:/Windows/Fonts/simkai.ttf",
            "C:/Windows/Fonts/Deng.ttf",
            // Linux（不同发行版/安装方式的常见落点）
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-SC-Regular.otf",
            "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc",
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            // macOS
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/STHeiti Medium.ttc",
            "/Library/Fonts/Songti.ttc",
    };

    // 扫描兜底时按文件名认中文字体；顺序即优先级（黑体类优先，衬线类靠后）
    private static final Pattern[] SCAN_PREFERENCE = {
            Pattern.compile("(?i)(simhei|msyh|yahei|notosanscjk|noto[-_ ]?sans[-_ ]?cjk|sourcehansans|source[-_ ]?han[-_ ]?sans|wqy|zenhei|microhei|pingfang|heiti)"),
            Pattern.compile("(?i)(simsun|songti|notoserifcjk|noto[-_ ]?serif[-_ ]?cjk|sourcehanserif|uming|ukai|simkai|simfang|deng|droidsansfallback)"),
    };
    private static final Pattern FONT_EXT = Pattern.compile("(?i)\\.(ttf|ttc|otf|otc)$");

    private static final String[] SCAN_DIRS = {
            "C:/Windows/Fonts",
            "/usr/share/fonts",
            "/usr/local/share/fonts",
            System.getProperty("user.home", "") + "/.fonts",
            System.getProperty("user.home", "") + "/.local/share/fonts",
            "/System/Library/Fonts",
            "/Library/Fonts",
    };

    /** 加载一个可渲染中文的字体；找不到抛 IllegalStateException（带排查指引）。 */
    public static PDFont load(PDDocument document) {
        // 0) 已缓存的路径直接用（文件被删则重新查找）
        File cached = cachedFontFile;
        if (cached != null && cached.isFile()) {
            PDFont f = tryLoad(document, cached);
            if (f != null) return f;
            cachedFontFile = null;
        }
        // 1) 显式指定
        String configured = System.getProperty("ywh.pdf.font", System.getenv("YWH_PDF_FONT"));
        if (configured != null && !configured.isBlank()) {
            PDFont f = remember(document, new File(configured));
            if (f != null) return f;
        }
        // 2) 常见路径
        for (String p : FIXED_CANDIDATES) {
            PDFont f = remember(document, new File(p));
            if (f != null) return f;
        }
        // 3) 扫描字体目录
        for (Pattern preference : SCAN_PREFERENCE) {
            for (String dir : SCAN_DIRS) {
                File d = new File(dir);
                if (!d.isDirectory()) continue;
                for (File candidate : listFonts(d, preference)) {
                    PDFont f = remember(document, candidate);
                    if (f != null) return f;
                }
            }
        }
        throw new IllegalStateException("服务器缺少中文字体，无法生成 PDF。请安装任一中文字体（如黑体/思源黑体/文泉驿），"
                + "或用环境变量 YWH_PDF_FONT（或 JVM 参数 -Dywh.pdf.font）指定字体文件路径");
    }

    private static List<File> listFonts(File dir, Pattern namePattern) {
        List<File> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir.toPath(), 3)) {
            walk.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(f -> FONT_EXT.matcher(f.getName()).find())
                    .filter(f -> namePattern.matcher(f.getName().toLowerCase(Locale.ROOT)).find())
                    .forEach(out::add);
        } catch (Exception ignored) { /* 无权限/坏链接：跳过该目录 */ }
        return out;
    }

    private static PDFont remember(PDDocument document, File f) {
        PDFont font = tryLoad(document, f);
        if (font != null) cachedFontFile = f;
        return font;
    }

    /** 尝试加载单个字体文件；ttc/otc 取集合中第一支，失败返回 null 继续找下一个。
     *  ⚠ .ttc 必须经 InputStream 整体读入内存（MemoryTTFDataStream）再解析：
     *  旧实现 new TrueTypeCollection(File) 用完即关，而 PDFBox 到 doc.save() 做字体子集化时
     *  才回头读字形数据——文件流已关闭，保存时 NPE（"Cannot invoke RandomAccessFile.getFilePointer"）。 */
    private static PDFont tryLoad(PDDocument document, File f) {
        if (f == null || !f.isFile()) return null;
        String name = f.getName().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".ttc") || name.endsWith(".otc")) {
                try (TrueTypeCollection collection = new TrueTypeCollection(
                        new BufferedInputStream(new FileInputStream(f)))) {
                    PDFont[] found = new PDFont[1];
                    collection.processAllFonts(ttf -> {
                        if (found[0] == null) {
                            try { found[0] = PDType0Font.load(document, ttf, true); } catch (IOException ignored) { }
                        }
                    });
                    return found[0];
                }
            }
            return PDType0Font.load(document, f); // .ttf/.otf：PDFBox 自己管理文件流生命周期（随文档关闭）
        } catch (Exception e) {
            return null; // 字体文件损坏/格式不支持：跳过
        }
    }
}
