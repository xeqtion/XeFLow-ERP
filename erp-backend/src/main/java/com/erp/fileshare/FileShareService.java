package com.erp.fileshare;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 资料共享服务 - 上传/下载/删除, 文件落本地磁盘, 元数据入库
 */
public interface FileShareService extends IService<SharedFile> {

    IPage<SharedFile> pageQuery(PageQuery query);

    SharedFile upload(MultipartFile file, String category, String remark, Integer expireDays) throws IOException;

    Path resolvePath(SharedFile f);

    void deleteFile(Long id);

    void cleanupExpiredFiles();

    @Slf4j
    @Service
    class Impl extends ServiceImpl<SharedFileMapper, SharedFile> implements FileShareService {

        private final FileShareProperties props;

        public Impl(FileShareProperties props) {
            this.props = props;
        }

        @Override
        public IPage<SharedFile> pageQuery(PageQuery query) {
            cleanupExpiredFiles();
            String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
            LambdaQueryWrapper<SharedFile> w = new LambdaQueryWrapper<SharedFile>()
                    .orderByDesc(SharedFile::getCreateTime);
            if (StringUtils.hasText(keyword)) {
                w.and(q -> q.like(SharedFile::getFileName, keyword)
                        .or().like(SharedFile::getCategory, keyword)
                        .or().like(SharedFile::getFileFormat, keyword)
                        .or().like(SharedFile::getUploaderName, keyword));
            }
            return page(new Page<>(query.getPageNum(), query.getPageSize()), w);
        }

        @Override
        public SharedFile upload(MultipartFile file, String category, String remark, Integer expireDays) throws IOException {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("文件为空");
            }
            int days = validateExpireDays(expireDays);
            long maxBytes = props.getMaxSizeMb() * 1024 * 1024;
            if (file.getSize() > maxBytes) {
                throw new BusinessException("文件超过大小上限 " + props.getMaxSizeMb() + "MB");
            }
            String original = StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename() : "未命名";
            com.erp.common.storage.LocalFileStorage.checkExtension(original, props.getBlockedExt());
            String ext = extensionWithDot(original);
            String stored = UUID.randomUUID().toString().replace("-", "") + ext;

            Path dir = rootDir();
            Files.createDirectories(dir);
            file.transferTo(resolveStored(stored));

            SharedFile f = new SharedFile();
            f.setFileName(original);
            f.setStoredName(stored);
            f.setContentType(file.getContentType());
            f.setFileFormat(fileFormat(original));
            f.setSize(file.getSize());
            f.setCategory(StringUtils.hasText(category) ? category.trim() : "无");
            f.setUploaderName(currentUserName());
            f.setRemark(remark);
            f.setExpireTime(LocalDateTime.now().plusDays(days));
            save(f);
            log.info("[FILESHARE] 上传 {} ({} bytes) -> {}", original, file.getSize(), stored);
            return f;
        }

        @Override
        public Path resolvePath(SharedFile f) {
            return resolveStored(f.getStoredName());
        }

        @Override
        public void deleteFile(Long id) {
            SharedFile f = getById(id);
            if (f == null) {
                throw new BusinessException("文件不存在");
            }
            try {
                Files.deleteIfExists(resolvePath(f));
            } catch (IOException e) {
                log.warn("[FILESHARE] 删除磁盘文件失败: {}", e.getMessage());
            }
            removeById(id);
        }

        @Override
        @Scheduled(cron = "${erp.fileshare.cleanup-cron:0 0 * * * ?}")
        public void cleanupExpiredFiles() {
            List<SharedFile> expired = list(new LambdaQueryWrapper<SharedFile>()
                    .isNotNull(SharedFile::getExpireTime)
                    .le(SharedFile::getExpireTime, LocalDateTime.now()));
            for (SharedFile f : expired) {
                try {
                    Files.deleteIfExists(resolvePath(f));
                } catch (IOException e) {
                    log.warn("[FILESHARE] 清理过期文件失败 {}: {}", f.getFileName(), e.getMessage());
                }
                removeById(f.getId());
            }
        }

        private int validateExpireDays(Integer expireDays) {
            int days = expireDays == null ? 30 : expireDays;
            if (days < 1 || days > 180) {
                throw new BusinessException("有效时长必须在 1 到 180 天之间");
            }
            return days;
        }

        private Path rootDir() {
            return Paths.get(props.getDir()).toAbsolutePath().normalize();
        }

        private Path resolveStored(String storedName) {
            Path root = rootDir();
            Path path = root.resolve(storedName == null ? "" : storedName).normalize();
            if (!path.startsWith(root)) {
                throw new BusinessException("非法文件路径");
            }
            return path;
        }

        private String extensionWithDot(String fileName) {
            int dot = fileName.lastIndexOf('.');
            if (dot < 0 || dot == fileName.length() - 1) {
                return "";
            }
            return fileName.substring(dot);
        }

        private String fileFormat(String fileName) {
            int dot = fileName.lastIndexOf('.');
            if (dot < 0 || dot == fileName.length() - 1) {
                return "无";
            }
            return fileName.substring(dot + 1).trim().toUpperCase();
        }

        private String currentUserName() {
            var u = SecurityUtils.getLoginUser();
            if (u == null) {
                return "系统";
            }
            String real = u.getUser().getRealName();
            return StringUtils.hasText(real) ? real : u.getUsername();
        }
    }
}
