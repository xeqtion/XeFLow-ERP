package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.CustomerFile;
import com.erp.basedata.mapper.CustomerFileMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.DataAccessGuard;
import com.erp.common.security.SecurityUtils;
import com.erp.common.storage.LocalFileStorage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * 客户附件服务 - 上传/列表/下载/删除
 */
public interface CustomerFileService extends IService<CustomerFile> {

    List<CustomerFile> listByCustomer(Long customerId);

    CustomerFile upload(Long customerId, MultipartFile file, String remark);

    Path resolvePath(CustomerFile f);

    void deleteFile(Long id);

    @Service
    class Impl extends ServiceImpl<CustomerFileMapper, CustomerFile> implements CustomerFileService {

        private final LocalFileStorage storage;
        private final DataAccessGuard accessGuard;

        public Impl(LocalFileStorage storage, DataAccessGuard accessGuard) {
            this.storage = storage;
            this.accessGuard = accessGuard;
        }

        @Override
        public List<CustomerFile> listByCustomer(Long customerId) {
            if (customerId == null) {
                throw new BusinessException("缺少客户ID");
            }
            accessGuard.assertCustomerVisible(customerId);
            return list(new LambdaQueryWrapper<CustomerFile>()
                    .eq(CustomerFile::getCustomerId, customerId)
                    .orderByDesc(CustomerFile::getCreateTime));
        }

        @Override
        public CustomerFile upload(Long customerId, MultipartFile file, String remark) {
            if (customerId == null) {
                throw new BusinessException("缺少客户ID");
            }
            accessGuard.assertCustomerVisible(customerId);
            LocalFileStorage.Stored s = storage.store(file);
            CustomerFile f = new CustomerFile();
            f.setCustomerId(customerId);
            f.setFileName(s.getOriginalName());
            f.setStoredName(s.getStoredName());
            f.setContentType(s.getContentType());
            f.setSize(s.getSize());
            f.setUploaderName(currentUserName());
            f.setRemark(remark);
            save(f);
            return f;
        }

        @Override
        public CustomerFile getById(Serializable id) {
            CustomerFile f = super.getById(id);
            if (f != null) {
                accessGuard.assertCustomerVisible(f.getCustomerId());
            }
            return f;
        }

        @Override
        public Path resolvePath(CustomerFile f) {
            return storage.resolve(f.getStoredName());
        }

        @Override
        public void deleteFile(Long id) {
            CustomerFile f = getById(id);
            if (f == null) {
                throw new BusinessException("文件不存在");
            }
            storage.delete(f.getStoredName());
            removeById(id);
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
