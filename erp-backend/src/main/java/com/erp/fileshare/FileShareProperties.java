package com.erp.fileshare;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 资料共享配置 (前缀 erp.fileshare)
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.fileshare")
public class FileShareProperties {

    /** 文件存储根目录 */
    private String dir = "./uploads/fileshare";

    /** 单文件大小上限(MB) */
    private long maxSizeMb = 50;

    /** 禁止上传的危险扩展名(可执行/脚本), 不区分大小写; 留空=不限制 */
    private List<String> blockedExt = List.of(
            "exe", "bat", "cmd", "com", "scr", "msi", "cpl", "jar",
            "js", "vbs", "vbe", "ps1", "psm1", "sh", "dll", "reg", "lnk", "hta");
}
