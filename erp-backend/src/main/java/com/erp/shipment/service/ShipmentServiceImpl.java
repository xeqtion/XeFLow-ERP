package com.erp.shipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.DataAccessGuard;
import com.erp.finance.service.PiPaymentService;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.mapper.ShippingInstructionMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.shipment.dto.ShipmentExcel;
import com.erp.shipment.entity.Shipment;
import com.erp.shipment.entity.ShipmentItem;
import com.erp.shipment.mapper.ShipmentItemMapper;
import com.erp.shipment.mapper.ShipmentMapper;
import com.erp.task.service.PersonalTaskService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
public class ShipmentServiceImpl extends ServiceImpl<ShipmentMapper, Shipment> implements ShipmentService {

    private final DataAccessGuard accessGuard;
    private final PersonalTaskService personalTaskService;
    private final SalesPiMapper salesPiMapper;
    private final ShippingInstructionMapper shippingInstructionMapper;
    private final PiPaymentService piPaymentService;
    private final ShipmentItemMapper shipmentItemMapper;

    public ShipmentServiceImpl(DataAccessGuard accessGuard,
                               PersonalTaskService personalTaskService,
                               SalesPiMapper salesPiMapper,
                               ShippingInstructionMapper shippingInstructionMapper,
                               PiPaymentService piPaymentService,
                               ShipmentItemMapper shipmentItemMapper) {
        this.accessGuard = accessGuard;
        this.personalTaskService = personalTaskService;
        this.salesPiMapper = salesPiMapper;
        this.shippingInstructionMapper = shippingInstructionMapper;
        this.piPaymentService = piPaymentService;
        this.shipmentItemMapper = shipmentItemMapper;
    }

    @Override
    public IPage<Shipment> pageQuery(PageQuery query) {
        IPage<Shipment> page = this.page(new Page<>(query.getPageNum(), query.getPageSize()), buildWrapper(query.getKeyword()));
        hydrateShippingInstructions(page.getRecords());
        return page;
    }

    @Override
    public List<Shipment> listByKeyword(String keyword) {
        List<Shipment> shipments = this.list(buildWrapper(keyword));
        hydrateShippingInstructions(shipments);
        return shipments;
    }

    @Override
    public Shipment getById(Serializable id) {
        Shipment shipment = super.getById(id);
        assertVisible(shipment);
        hydrateShippingInstructions(shipment == null ? List.of() : List.of(shipment));
        return shipment;
    }

    @Override
    public boolean save(Shipment entity) {
        throw new BusinessException("请从 PI 合同的“生成出货”功能创建出货单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Shipment entity) {
        if (entity == null || entity.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        Shipment existing = super.getById(entity.getId());
        if (existing == null) {
            throw new BusinessException("出货单不存在");
        }
        assertVisible(existing);
        validateStatusTransition(existing.getStatus(), entity.getStatus());
        // Generated relationships are immutable; cached clients may submit rounded IDs.
        entity.setPiId(existing.getPiId());
        entity.setSiId(existing.getSiId());
        entity.setShipmentNo(existing.getShipmentNo());
        entity.setStatus(existing.getStatus());
        entity.setCreateBy(existing.getCreateBy());
        entity.setCreateTime(existing.getCreateTime());
        resolvePiReference(entity);
        validateReferences(entity);
        boolean updated = super.updateById(entity);
        if (updated) {
            Shipment current = super.getById(entity.getId());
            syncPiStatus(current);
            syncCalendarTask(current);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        Shipment existing = super.getById(id);
        assertVisible(existing);
        if (existing != null && !isDeletableStatus(existing.getStatus())) {
            throw new BusinessException("出货单仅草稿或全部出货状态可以删除；部分出货等中间状态不能删除");
        }
        boolean removed = super.removeById(id);
        if (removed && id instanceof Long longId) {
            shipmentItemMapper.delete(new LambdaQueryWrapper<ShipmentItem>()
                    .eq(ShipmentItem::getShipmentId, longId));
            personalTaskService.removeSystemTask("SHIPMENT", longId);
        }
        return removed;
    }

    private void syncCalendarTask(Shipment entity) {
        if (entity == null || entity.getId() == null) {
            return;
        }
        String title = "出货单: " + (StringUtils.hasText(entity.getShipmentNo()) ? entity.getShipmentNo() : "未命名单据");
        String content = "出货日期 " + entity.getShipDate()
                + "\n数量: " + (entity.getQty() == null ? "-" : entity.getQty().stripTrailingZeros().toPlainString())
                + "\n箱数: " + (entity.getPackageCount() == null ? "-" : entity.getPackageCount());
        personalTaskService.upsertSystemTask(resolveOwnerId(entity), title, content, entity.getShipDate(),
                "HIGH", "SHIPMENT", "SHIPMENT", entity.getId(), entity.getShipmentNo());
    }

    private Long resolveOwnerId(Shipment entity) {
        if (entity.getPiId() != null) {
            SalesPi pi = salesPiMapper.selectById(entity.getPiId());
            if (pi != null && pi.getOwnerId() != null) {
                return pi.getOwnerId();
            }
        }
        return entity.getCreateBy();
    }

    private LambdaQueryWrapper<Shipment> buildWrapper(String keyword) {
        LambdaQueryWrapper<Shipment> wrapper = new LambdaQueryWrapper<Shipment>()
                .orderByDesc(Shipment::getCreateTime);
        Long currentUserId = accessGuard.restrictOwnerId();
        if (currentUserId != null) {
            List<Long> piIds = accessGuard.visiblePiIdsForCurrentUser();
            wrapper.and(w -> {
                if (!piIds.isEmpty()) {
                    w.in(Shipment::getPiId, piIds).or();
                }
                w.isNull(Shipment::getPiId).eq(Shipment::getCreateBy, currentUserId);
            });
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Shipment::getShipmentNo, keyword);
        }
        return wrapper;
    }

    private void validateReferences(Shipment entity) {
        if (entity == null) {
            return;
        }
        if (entity.getPiId() == null) {
            throw new BusinessException("出货单必须关联 PI");
        }
        accessGuard.assertPiVisible(entity.getPiId());
        SalesPi pi = salesPiMapper.selectById(entity.getPiId());
        piPaymentService.assertFullyPaid(pi);
    }

    private void resolvePiReference(Shipment entity) {
        if (entity == null || entity.getPiId() != null || entity.getSiId() == null) {
            return;
        }
        ShippingInstruction instruction = shippingInstructionMapper.selectById(entity.getSiId());
        if (instruction == null) {
            throw new BusinessException("关联出货指示不存在");
        }
        if (entity.getPiId() != null && instruction.getPiId() != null && !entity.getPiId().equals(instruction.getPiId())) {
            throw new BusinessException("出货单关联的 PI 必须与出货指示一致");
        }
        entity.setPiId(instruction.getPiId());
    }

    private void validateStatusTransition(String previous, String next) {
        if (!StringUtils.hasText(next) || !StringUtils.hasText(previous) || previous.equals(next)) {
            return;
        }
        throw new BusinessException("出货状态由库存发货结果自动确定，不能手工修改");
    }

    private void syncPiStatus(Shipment shipment) {
        if (shipment == null || shipment.getPiId() == null) {
            return;
        }
        SalesPi pi = salesPiMapper.selectById(shipment.getPiId());
        if (pi == null) {
            return;
        }
        if ("DELIVERED".equals(shipment.getStatus()) || "SHIPPED".equals(shipment.getStatus())
                || "PARTIAL_SHIPPED".equals(shipment.getStatus())) {
            pi.setStatus("SHIPPED");
            salesPiMapper.updateById(pi);
        } else if ("COMPLETED".equals(shipment.getStatus()) || "FULL_SHIPPED".equals(shipment.getStatus())) {
            pi.setStatus("COMPLETED");
            salesPiMapper.updateById(pi);
        }
    }

    private boolean isDeletableStatus(String status) {
        return !StringUtils.hasText(status) || Set.of("DRAFT", "FULL_SHIPPED", "COMPLETED").contains(status);
    }

    private void hydrateShippingInstructions(List<Shipment> shipments) {
        List<Long> ids = shipments.stream().map(Shipment::getSiId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> numbers = shippingInstructionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ShippingInstruction::getId, ShippingInstruction::getSiNo, (a, b) -> a));
        shipments.forEach(shipment -> shipment.setSiNo(numbers.get(shipment.getSiId())));
    }

    private String nextShipmentNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String shipmentNo;
        do {
            shipmentNo = "SHP" + date + "-" + String.format("%05d", ThreadLocalRandom.current().nextInt(100000));
        } while (this.exists(new LambdaQueryWrapper<Shipment>().eq(Shipment::getShipmentNo, shipmentNo)));
        return shipmentNo;
    }

    private void assertVisible(Shipment entity) {
        if (entity == null) {
            return;
        }
        accessGuard.assertRecordVisible(entity.getPiId(), null, entity.getCreateBy(), "出货单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile file) throws IOException {
        ImportResult result = new ImportResult();
        List<ShipmentExcel> rows = new ArrayList<>();
        ExcelUtil.read(file.getInputStream(), ShipmentExcel.class, 500, rows::addAll);
        result.setTotal(rows.size());

        Set<String> existNos = new HashSet<>();
        this.list(new LambdaQueryWrapper<Shipment>().select(Shipment::getShipmentNo))
                .forEach(s -> existNos.add(s.getShipmentNo()));

        List<Shipment> toSave = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ShipmentExcel row = rows.get(i);
            int rowNo = i + 1;
            if (!StringUtils.hasText(row.getShipmentNo())) {
                result.addError(rowNo, "出货单号不能为空");
                continue;
            }
            if (existNos.contains(row.getShipmentNo())) {
                result.addError(rowNo, "出货单号重复: " + row.getShipmentNo());
                continue;
            }
            Shipment shipment = new Shipment();
            BeanUtils.copyProperties(row, shipment);
            if (!StringUtils.hasText(shipment.getStatus())) {
                shipment.setStatus("DRAFT");
            }
            existNos.add(row.getShipmentNo());
            toSave.add(shipment);
            result.addSuccess();
        }
        if (!toSave.isEmpty()) {
            this.saveBatch(toSave);
        }
        return result;
    }
}
