package com.erp.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.SecurityUtils;
import com.erp.common.storage.LocalFileStorage;
import com.erp.order.entity.OrderOem;
import com.erp.order.entity.OrderOemFile;
import com.erp.order.mapper.OrderOemFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

public interface OrderOemFileService extends IService<OrderOemFile> {

    List<OrderOemFile> listByOem(Long oemId);

    OrderOemFile upload(OrderOem oem, MultipartFile file, String remark);

    Path resolvePath(OrderOemFile file);

    void deleteFile(Long id);

    void deleteByOem(Long oemId);

    @Service
    class Impl extends ServiceImpl<OrderOemFileMapper, OrderOemFile> implements OrderOemFileService {

        private final LocalFileStorage storage;

        public Impl(LocalFileStorage storage) {
            this.storage = storage;
        }

        @Override
        public List<OrderOemFile> listByOem(Long oemId) {
            return list(new LambdaQueryWrapper<OrderOemFile>()
                    .eq(OrderOemFile::getOemId, oemId)
                    .orderByDesc(OrderOemFile::getCreateTime));
        }

        @Override
        public OrderOemFile upload(OrderOem oem, MultipartFile file, String remark) {
            if (oem == null || oem.getId() == null) {
                throw new BusinessException("OEM 记录不存在");
            }
            LocalFileStorage.Stored stored = storage.store(file);
            OrderOemFile entity = new OrderOemFile();
            entity.setOemId(oem.getId());
            entity.setFileName(stored.getOriginalName());
            entity.setStoredName(stored.getStoredName());
            entity.setContentType(stored.getContentType());
            entity.setSize(stored.getSize());
            entity.setUploaderName(currentUserName());
            entity.setRemark(remark);
            save(entity);
            return entity;
        }

        @Override
        public Path resolvePath(OrderOemFile file) {
            return storage.resolve(file.getStoredName());
        }

        @Override
        public void deleteFile(Long id) {
            OrderOemFile file = getById(id);
            if (file == null) {
                throw new BusinessException("文件不存在");
            }
            storage.delete(file.getStoredName());
            removeById(id);
        }

        @Override
        public void deleteByOem(Long oemId) {
            for (OrderOemFile file : listByOem(oemId)) {
                storage.delete(file.getStoredName());
            }
            remove(new LambdaQueryWrapper<OrderOemFile>().eq(OrderOemFile::getOemId, oemId));
        }

        private String currentUserName() {
            var user = SecurityUtils.getLoginUser();
            if (user == null) {
                return "系统";
            }
            String realName = user.getUser().getRealName();
            return StringUtils.hasText(realName) ? realName : user.getUsername();
        }
    }
}
