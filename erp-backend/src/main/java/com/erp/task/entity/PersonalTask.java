package com.erp.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 个人待办/计划 - 按日期排布在首页日历与时间线, 每个用户只看到自己的
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("personal_task")
public class PersonalTask extends BaseEntity {

    /** 雪花ID序列化为字符串, 避免前端精度丢失致 toggle/编辑/删除失效 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() {
        return super.getId();
    }

    /** 归属用户(创建者) */
    private Long ownerId;

    /** 标题 */
    private String title;

    /** 详情 */
    private String content;

    /** 计划日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate taskDate;

    /** 是否全天: 1 全天 / 0 指定时间段 */
    private Integer allDay;

    /** 开始时间 */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    /** 地点 */
    private String location;

    /** 优先级: LOW / NORMAL / HIGH */
    private String priority;

    private String category;

    private String sourceType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;

    private String sourceNo;

    private Integer systemGenerated;

    /** 状态: PENDING 待办 / DONE 已完成 */
    private String status;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime doneTime;
}
