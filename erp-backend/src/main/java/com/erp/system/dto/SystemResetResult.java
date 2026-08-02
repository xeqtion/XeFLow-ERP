package com.erp.system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SystemResetResult {

    private boolean usersPreserved;
    private boolean currentSuperAdminRetained;
    private long retainedUserCount;
    private int clearedRowCount;
    private LocalDateTime completedAt;
}
