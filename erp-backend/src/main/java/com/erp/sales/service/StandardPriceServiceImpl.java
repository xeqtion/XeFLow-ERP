package com.erp.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.sales.entity.StandardPrice;
import com.erp.sales.mapper.StandardPriceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
public class StandardPriceServiceImpl extends ServiceImpl<StandardPriceMapper, StandardPrice>
        implements StandardPriceService {

    private final ProductMapper productMapper;
    private final SmartStandardPriceImportService smartImportService;

    public StandardPriceServiceImpl(ProductMapper productMapper,
                                    SmartStandardPriceImportService smartImportService) {
        this.productMapper = productMapper;
        this.smartImportService = smartImportService;
    }

    @Override
    public IPage<StandardPrice> pageQuery(PageQuery query) {
        return this.page(new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query.getKeyword()));
    }

    @Override
    public List<StandardPrice> listByKeyword(String keyword) {
        return this.list(buildWrapper(keyword));
    }

    private LambdaQueryWrapper<StandardPrice> buildWrapper(String keyword) {
        LambdaQueryWrapper<StandardPrice> wrapper = new LambdaQueryWrapper<StandardPrice>()
                .orderByDesc(StandardPrice::getCreateTime)
                .orderByAsc(StandardPrice::getProductId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(StandardPrice::getProductName, kw)
                    .or().like(StandardPrice::getCompanyModel, kw)
                    .or().like(StandardPrice::getRemark, kw)
                    .or().like(StandardPrice::getProdCode, kw));
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveQuote(StandardPrice quote) {
        quote.setId(null);
        prepareQuote(quote);
        this.save(quote);
        return quote.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuote(StandardPrice quote) {
        if (quote.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        if (this.getById(quote.getId()) == null) {
            throw new BusinessException("产品报价不存在");
        }
        prepareQuote(quote);
        this.updateById(quote);
    }

    private void prepareQuote(StandardPrice quote) {
        if (quote.getProductId() == null) {
            throw new BusinessException("产品ID不能为空");
        }
        Product product = productMapper.selectById(quote.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在: " + quote.getProductId());
        }

        quote.setProductName(StringUtils.hasText(quote.getProductName())
                ? quote.getProductName().trim()
                : product.getProductName());
        quote.setCompanyModel(StringUtils.hasText(quote.getCompanyModel())
                ? quote.getCompanyModel().trim()
                : product.getCompanyModel());
        if (StringUtils.hasText(product.getProdCode())) {
            quote.setProdCode(product.getProdCode());
        }

        BigDecimal standardUnitPrice = quote.getStandardUnitPrice();
        if (standardUnitPrice == null) {
            throw new BusinessException("标准单价不能为空");
        }
        quote.setStandardUnitPrice(standardUnitPrice);
        quote.setPrice(standardUnitPrice);

        if (quote.getDiscountUnitPrice() == null) {
            throw new BusinessException("折扣单价不能为空");
        }
        if (quote.getFloorPrice() == null) {
            quote.setFloorPrice(quote.getDiscountUnitPrice());
        }
        if (StringUtils.hasText(quote.getCurrency())) {
            quote.setCurrency(quote.getCurrency().trim().toUpperCase());
        } else {
            quote.setCurrency("USD");
        }
    }

    @Override
    public StandardPrice findApplicable(Long productId, Long customerId) {
        if (productId == null) {
            return null;
        }
        return this.baseMapper.findApplicable(productId, customerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile file) throws IOException {
        return smartImportService.compatImport(file);
    }
}
