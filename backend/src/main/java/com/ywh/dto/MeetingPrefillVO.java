package com.ywh.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * "新建会议 - 上传文档/拍照识别"接口返回：OCR + 大模型抽取出的会议信息，供前端预填表单。
 * available=false 时表示 OCR/AI 未开启或失败，前端据 message 提示后回退手动填写。
 */
@Data
public class MeetingPrefillVO {

    /** true=识别并抽取成功；false=未开启/失败（看 message，可能仅带 ocrText）。 */
    private boolean available;
    /** 提示文案（成功提示或未开启/失败原因），前端可直接 toast。 */
    private String message = "";

    private String title = "";
    private String meetingDate = "";   // yyyy-MM-dd
    private String meetingTime = "";   // HH:mm（24 小时制）
    private String location = "";
    private List<String> topics = new ArrayList<>();

    /** OCR 识别到的原文；AI 未开启/抽取失败时回传，便于前端展示或手动整理。 */
    private String ocrText = "";

    /** AI 判类：notice=会议通知（可预填表单）/ material=会议材料（供委员传阅）/ ""=未判出。 */
    private String category = "";
    /** 多份通知的会议时间/地点互相矛盾时的一句话说明；不矛盾或单份通知为空串。 */
    private String conflictNote = "";
    /** 本次大模型抽取消耗的 token（Ark usage.total_tokens；未调用/失败为 0）。 */
    private long tokens;

    /** 文件已落库存储的公网 URL（无论识别成败都会存，便于直接作为会议材料挂载）。 */
    private String fileUrl = "";
    private String fileName = "";
    private String fileType = "";
    private long fileSize;

    /**
     * 多文件识别（/parse-documents）时，逐个文件的信息与判类，顺序与上传顺序一致。
     * 单文件接口(/parse-document)不填此列表。
     */
    private List<FileInfo> files = new ArrayList<>();

    /** 单个上传文件的落库信息 + AI 判类。 */
    @Data
    public static class FileInfo {
        private String fileUrl = "";
        private String fileName = "";
        private String fileType = "";
        private long fileSize;
        /** AI 判类：notice=会议通知 / material=会议材料 / ""=未判出。 */
        private String category = "";
    }

    public static MeetingPrefillVO unavailable(String message) {
        MeetingPrefillVO vo = new MeetingPrefillVO();
        vo.setAvailable(false);
        vo.setMessage(message);
        return vo;
    }

    /** OCR 出了文字但没做（或没做成）AI 抽取：回传原文供手填。 */
    public static MeetingPrefillVO textOnly(String ocrText, String message) {
        MeetingPrefillVO vo = new MeetingPrefillVO();
        vo.setAvailable(false);
        vo.setMessage(message);
        vo.setOcrText(ocrText == null ? "" : ocrText);
        return vo;
    }
}
