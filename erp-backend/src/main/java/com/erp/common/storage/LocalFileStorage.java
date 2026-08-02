package com.erp.common.storage;

import com.erp.common.exception.BusinessException;
import com.erp.fileshare.FileShareProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 通用本地文件存储 - 落磁盘(UUID 命名), 复用 erp.fileshare 配置的根目录与大小上限。
 * 元数据由各业务表自行保存; 本类只管字节落盘/读取/删除。
 */
@Slf4j
@Component
public class LocalFileStorage {

    private final FileShareProperties props;

    public LocalFileStorage(FileShareProperties props) {
        this.props = props;
    }

    /** 保存上传文件, 返回存储元信息(磁盘名/原名/类型/大小) */
    public Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        long maxBytes = props.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("文件超过大小上限 " + props.getMaxSizeMb() + "MB");
        }
        String original = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "未命名";
        checkExtension(original, props.getBlockedExt());
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Path dir = rootDir();
            Files.createDirectories(dir);
            file.transferTo(resolve(stored));
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
        Stored s = new Stored();
        s.setStoredName(stored);
        s.setOriginalName(original);
        s.setContentType(file.getContentType());
        s.setSize(file.getSize());
        log.info("[STORAGE] 保存 {} ({} bytes) -> {}", original, file.getSize(), stored);
        return s;
    }

    public Path resolve(String storedName) {
        Path root = rootDir();
        Path path = root.resolve(storedName == null ? "" : storedName).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException("非法文件路径");
        }
        return path;
    }

    private Path rootDir() {
        return Paths.get(props.getDir()).toAbsolutePath().normalize();
    }

    /** 校验扩展名是否在禁止列表(危险可执行/脚本); 命中则抛业务异常 */
    public static void checkExtension(String originalName, java.util.List<String> blockedExt) {
        if (blockedExt == null || blockedExt.isEmpty() || originalName == null) {
            return;
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return;
        }
        String ext = originalName.substring(dot + 1).toLowerCase();
        for (String blocked : blockedExt) {
            if (ext.equals(blocked.trim().toLowerCase())) {
                throw new BusinessException("出于安全考虑, 不允许上传该类型文件: ." + ext);
            }
        }
    }

    public void delete(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException e) {
            log.warn("[STORAGE] 删除磁盘文件失败: {}", e.getMessage());
        }
    }

    @Data
    public static class Stored {
        private String storedName;
        private String originalName;
        private String contentType;
        private Long size;
    }
}
