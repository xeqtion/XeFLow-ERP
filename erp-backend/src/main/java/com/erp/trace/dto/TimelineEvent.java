package com.erp.trace.dto;

import lombok.Data;

/**
 * 单据时间线事件
 */
@Data
public class TimelineEvent {

    /** 节点类型: PI/SI/SHIPMENT/PAYMENT */
    private String node;

    /** 节点中文标签 */
    private String nodeLabel;

    /** 单据号 */
    private String docNo;

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 状态 */
    private String status;

    /** 摘要说明 */
    private String summary;

    public TimelineEvent(String node, String nodeLabel, String docNo, String date, String status, String summary) {
        this.node = node;
        this.nodeLabel = nodeLabel;
        this.docNo = docNo;
        this.date = date;
        this.status = status;
        this.summary = summary;
    }
}
