package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.erp.basedata.entity.Bom;
import com.erp.basedata.mapper.BomMapper;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BomServiceImplTest {

    private BomMapper mapper;
    private BomServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(BomMapper.class);
        service = new BomServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void doesNotReuseCodesFromLogicallyDeletedRecords() {
        when(mapper.selectAllCodes()).thenReturn(List.of("MT0001"));
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(mapper.insert(any(Bom.class))).thenReturn(1);

        Bom bom = new Bom();
        service.saveBom(bom);

        assertThat(bom.getBomCode()).isEqualTo("MT0002");
    }

    @Test
    void archivesPreviousTombstoneBeforeDeletingReusedCode() {
        Bom bom = new Bom();
        bom.setId(2L);
        bom.setBomCode("MT0001");
        when(mapper.selectById(2L)).thenReturn(bom);
        when(mapper.archiveDeletedCodeConflicts("MT0001")).thenReturn(1);
        when(mapper.deleteById((Serializable) 2L)).thenReturn(1);

        service.removeBom(2L);

        var ordered = inOrder(mapper);
        ordered.verify(mapper).selectById(2L);
        ordered.verify(mapper).archiveDeletedCodeConflicts("MT0001");
        ordered.verify(mapper).deleteById(eq((Serializable) 2L));
    }

    @Test
    void deleteReturnsReadableMessageInsteadOfGenericSystemError() {
        Bom bom = new Bom();
        bom.setId(2L);
        bom.setBomCode("MT0001");
        when(mapper.selectById(2L)).thenReturn(bom);
        when(mapper.archiveDeletedCodeConflicts("MT0001"))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.removeBom(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("删除失败")
                .hasMessageNotContaining("duplicate");
    }

    @Test
    void importGeneratesCodeAfterDeletedHistory() throws Exception {
        when(mapper.selectList(nullable(Wrapper.class))).thenReturn(List.of());
        when(mapper.selectAllCodes()).thenReturn(List.of("MT0001"));
        when(mapper.insert(any(Bom.class))).thenReturn(1);

        ImportResult result = service.importExcel(workbook(
                new Object[]{"", "Adapter", "否", "XF-A", "12V", "2", "Factory", "123", "Lee", "3.5", "CNY", ""}));

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFail()).isZero();
        ArgumentCaptor<Bom> captor = ArgumentCaptor.forClass(Bom.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getBomCode()).isEqualTo("MT0002");
        assertThat(captor.getValue().getNameCn()).isEqualTo("Adapter");
    }

    @Test
    void importRejectsExplicitCodeThatOnlyExistsInDeletedHistory() throws Exception {
        when(mapper.selectList(nullable(Wrapper.class))).thenReturn(List.of());
        when(mapper.selectAllCodes()).thenReturn(List.of("MT0001"));

        ImportResult result = service.importExcel(workbook(
                new Object[]{"MT0001", "Adapter", "否", "XF-A", "", "", "", "", "", "", "CNY", ""}));

        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFail()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("已被使用并删除");
        verify(mapper, never()).insert(any(Bom.class));
    }

    @Test
    void invalidRowDoesNotPreventFollowingRowsFromImporting() throws Exception {
        when(mapper.selectList(nullable(Wrapper.class))).thenReturn(List.of());
        when(mapper.selectAllCodes()).thenReturn(List.of());
        when(mapper.insert(any(Bom.class))).thenReturn(1);

        ImportResult result = service.importExcel(workbook(
                new Object[]{"", "Broken", "否", "", "", "not-a-number", "", "", "", "", "CNY", ""},
                new Object[]{"", "Valid", "是", "", "", "1", "", "", "", "2", "CNY", ""}));

        assertThat(result.getFail()).isEqualTo(1);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("用量必须是数字");
        verify(mapper).insert(any(Bom.class));
    }

    private MockMultipartFile workbook(Object[]... rows) throws Exception {
        String[] headers = {
                "物料编码（选填）", "名称", "是否通用", "支持产品型号", "规格", "用量",
                "供应商", "联系电话", "联系人", "单价", "币种", "备注"
        };
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("配件");
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                var excelRow = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    excelRow.createCell(column).setCellValue(String.valueOf(rows[rowIndex][column]));
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "bom.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
