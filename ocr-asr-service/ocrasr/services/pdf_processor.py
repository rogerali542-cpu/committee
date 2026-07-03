"""
PDF -> 逐页图片渲染 (供整份材料 OCR 用)
会议材料常是扫描件/拍照 PDF, 视觉模型只吃图片, 这里用 PyMuPDF 把每页渲染成 PNG bytes,
再交给 ocr_client.ocr_image 逐页识别. 仅做渲染, 不识别、不写库.
"""
from __future__ import annotations

from typing import List, Tuple

from loguru import logger

# 单份 PDF 最多渲染识别页数, 防超大文档拖垮服务 (会议材料一般 1~5 页)
MAX_PDF_PAGES = 20
# 渲染 DPI: 160 对 A4 约 1300x1840px, 文字清晰且单页 PNG 一般 < 3MB (低于 OCR 8MB 上限)
DEFAULT_DPI = 160


class PdfProcessError(Exception):
    """PDF 解析/渲染失败 (文件损坏 / 非 PDF / PyMuPDF 异常)"""


def render_pdf_to_images(
    pdf_bytes: bytes,
    *,
    dpi: int = DEFAULT_DPI,
    max_pages: int = MAX_PDF_PAGES,
) -> Tuple[List[bytes], int]:
    """
    把 PDF 每页渲染成 PNG bytes.

    入参: pdf_bytes 原始 PDF 字节
    出参: (images, total_pages)
        - images: list[png_bytes], 按页顺序, 最多 max_pages 张
        - total_pages: PDF 实际总页数 (用于判断是否被截断)

    失败统一抛 PdfProcessError, 由上游兜底为"识别失败".
    """
    if not pdf_bytes:
        raise PdfProcessError("PDF 内容为空")

    # 延迟导入: PyMuPDF 体积大, 只有真遇到 PDF 才加载
    try:
        import fitz  # PyMuPDF
    except ImportError as e:  # noqa: BLE001
        raise PdfProcessError("服务未安装 PyMuPDF, 无法渲染 PDF") from e

    try:
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as e:  # noqa: BLE001
        raise PdfProcessError(f"PDF 解析失败: {e!s}") from e

    images: List[bytes] = []
    try:
        total_pages = doc.page_count
        n = min(total_pages, max_pages)
        zoom = dpi / 72.0
        mat = fitz.Matrix(zoom, zoom)
        for i in range(n):
            page = doc.load_page(i)
            # alpha=False 出不带透明通道的 RGB, 体积更小, OCR 不需要 alpha
            pix = page.get_pixmap(matrix=mat, alpha=False)
            images.append(pix.tobytes("png"))
    except Exception as e:  # noqa: BLE001
        raise PdfProcessError(f"PDF 渲染失败: {e!s}") from e
    finally:
        doc.close()

    logger.info(
        "[pdf] rendered {}/{} pages @ {}dpi, sizes_kb={}",
        len(images), total_pages, dpi,
        [len(b) // 1024 for b in images],
    )
    return images, total_pages
