package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final SmartProductImportService smartImportService;

    public ProductServiceImpl(SmartProductImportService smartImportService) {
        this.smartImportService = smartImportService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product createProduct(Product product) {
        product.setId(baseMapper.selectNextProductId());
        validateProductId(product.getId());
        prepareForSave(product);
        this.save(product);
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Product product) {
        if (product.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        updateProduct(product.getId(), product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Long originalId, Product product) {
        if (originalId == null) {
            throw new BusinessException("缺少原产品ID");
        }
        if (product == null || product.getId() == null) {
            throw new BusinessException("缺少产品ID");
        }
        validateProductId(product.getId());
        Product existing = super.getById(originalId);
        if (existing == null) {
            throw new BusinessException("产品不存在: " + originalId);
        }
        Long newId = product.getId();
        if (!originalId.equals(newId) && super.getById(newId) != null) {
            throw new BusinessException("产品ID已存在: " + newId);
        }
        prepareForSave(product);
        if (originalId.equals(newId)) {
            this.updateById(product);
            return;
        }
        product.setId(originalId);
        this.updateById(product);
        updateReferences(originalId, newId);
        baseMapper.updateProductId(originalId, newId);
    }

    @Override
    public Product getById(Serializable id) {
        Product product = super.getById(id);
        fillDisplayFields(product);
        return product;
    }

    @Override
    public IPage<Product> pageQuery(PageQuery query) {
        IPage<Product> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query.getKeyword()));
        page.getRecords().forEach(this::fillDisplayFields);
        return page;
    }

    @Override
    public List<Product> listByKeyword(String keyword) {
        List<Product> list = this.list(buildWrapper(keyword));
        list.forEach(this::fillDisplayFields);
        return list;
    }

    private LambdaQueryWrapper<Product> buildWrapper(String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .orderByAsc(Product::getId)
                .orderByDesc(Product::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> {
                w.like(Product::getProductName, kw)
                    .or().like(Product::getCompanyModel, kw)
                    .or().like(Product::getFactoryModel, kw)
                    .or().like(Product::getProdCode, kw)
                    .or().like(Product::getProdNameCn, keyword)
                    .or().like(Product::getProdNameEn, keyword)
                    .or().like(Product::getSpec, keyword);
                if (kw.matches("\\d+")) {
                    try {
                        w.or().eq(Product::getId, Long.parseLong(kw));
                    } catch (NumberFormatException ignored) {
                        // Too large for Long; text fields have already been searched.
                    }
                }
            });
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile file) throws IOException {
        return smartImportService.compatImport(file);
    }

    private void validateProductId(Long id) {
        if (id == null || id < 100000L || id > 999999999L) {
            throw new BusinessException("产品ID必须是6到9位数字");
        }
    }

    private void updateReferences(Long oldId, Long newId) {
        baseMapper.updateBomProductId(oldId, newId);
        baseMapper.updateContractPriceProductId(oldId, newId);
        baseMapper.updateOrderOemProductId(oldId, newId);
        baseMapper.updatePurchaseOrderItemProductId(oldId, newId);
        baseMapper.updateSalesPiItemProductId(oldId, newId);
        baseMapper.updateStandardPriceProductId(oldId, newId);
        baseMapper.updateStockMovementProductId(oldId, newId);
    }

    private void prepareForSave(Product product) {
        syncLegacyFields(product);
        if (!StringUtils.hasText(product.getCurrency())) {
            product.setCurrency("CNY");
        }
        if (!StringUtils.hasText(product.getProdCode()) && product.getId() != null) {
            product.setProdCode(firstText(product.getFactoryModel(), String.valueOf(product.getId())));
        }
    }

    private void syncLegacyFields(Product product) {
        if (product == null) {
            return;
        }
        if (!StringUtils.hasText(product.getProductName())) {
            product.setProductName(firstText(product.getProdNameCn(), product.getProdNameEn()));
        }
        if (!StringUtils.hasText(product.getCompanyModel())) {
            product.setCompanyModel(product.getSpec());
        }
        if (!StringUtils.hasText(product.getFactoryModel()) && StringUtils.hasText(product.getProdCode())) {
            product.setFactoryModel(product.getProdCode());
        }
        if (product.getFactoryPrice() == null) {
            product.setFactoryPrice(product.getCostPrice());
        }
        if (!StringUtils.hasText(product.getProdNameCn())) {
            product.setProdNameCn(product.getProductName());
        }
        if (!StringUtils.hasText(product.getSpec())) {
            product.setSpec(product.getCompanyModel());
        }
        if (product.getCostPrice() == null) {
            product.setCostPrice(product.getFactoryPrice());
        }
    }

    private void fillDisplayFields(Product product) {
        if (product == null) {
            return;
        }
        if (!StringUtils.hasText(product.getProductName())) {
            product.setProductName(firstText(product.getProdNameCn(), product.getProdNameEn()));
        }
        if (!StringUtils.hasText(product.getCompanyModel())) {
            product.setCompanyModel(product.getSpec());
        }
        if (!StringUtils.hasText(product.getFactoryModel()) && StringUtils.hasText(product.getProdCode())) {
            product.setFactoryModel(product.getProdCode());
        }
        if (product.getFactoryPrice() == null) {
            product.setFactoryPrice(product.getCostPrice());
        }
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }
}
