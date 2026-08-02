package com.erp.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "报表查询条件")
public class ReportQuery {

    @Schema(description = "维度: DAY/WEEK/MONTH/QUARTER/YEAR", example = "MONTH")
    private String dimension = "MONTH";

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

}
