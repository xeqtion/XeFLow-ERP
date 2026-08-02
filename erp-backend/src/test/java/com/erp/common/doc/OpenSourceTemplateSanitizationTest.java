package com.erp.common.doc;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.PictureData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSourceTemplateSanitizationTest {

    private static final List<String> FORBIDDEN = List.of(
            "gy-hitec",
            "hitec lighting",
            "dodoliu",
            "adj products",
            "012-581-9-208197-7",
            "eubank plaza",
            "tianan cyber park",
            "47.113.112.66",
            "gy-hitecerp.xin",
            "gy-hiteclighting",
            "@163.com",
            "@outlook.com"
    );

    @Test
    void bundledDocumentTemplatesDoNotContainPrivateText() throws Exception {
        verifyWorkbook("templates/excel/pi_ci_po_template.xlsx", "pi-ci-po");
        verifyWorkbook("templates/shipping-instruction.xlsx", "shipping-instruction");
    }

    private void verifyWorkbook(String resourcePath, String outputPrefix) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream input = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(input)) {
            List<String> violations = new ArrayList<>();
            List<String> cells = new ArrayList<>();
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            cells.add(sheet.getSheetName() + "!" + cell.getAddress() + "="
                                    + cell.getStringCellValue());
                            findViolations(violations, sheet.getSheetName() + "!" + cell.getAddress(),
                                    cell.getStringCellValue());
                        }
                        if (cell.getCellComment() != null) {
                            findViolations(violations, sheet.getSheetName() + "!" + cell.getAddress() + " comment",
                                    cell.getCellComment().getString().getString());
                        }
                    }
                }
                findViolations(violations, sheet.getSheetName() + " header/footer",
                        sheet.getHeader().getLeft() + sheet.getHeader().getCenter() + sheet.getHeader().getRight()
                                + sheet.getFooter().getLeft() + sheet.getFooter().getCenter()
                                + sheet.getFooter().getRight());
            }
            String outputDirectory = System.getProperty("xeflow.template.audit-output");
            if (outputDirectory != null && !outputDirectory.isBlank()) {
                Path directory = Path.of(outputDirectory);
                Files.createDirectories(directory);
                Files.write(directory.resolve(outputPrefix + "-cells.txt"), cells);
                int index = 0;
                for (PictureData picture : workbook.getAllPictures()) {
                    String extension = picture.suggestFileExtension();
                    Files.write(directory.resolve(outputPrefix + "-" + index++ + "." + extension),
                            picture.getData());
                }
            }
            assertThat(violations).isEmpty();
        }
    }

    private void findViolations(List<String> violations, String location, String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN) {
            if (normalized.contains(forbidden)) {
                violations.add(location + " contains " + forbidden + ": " + value);
            }
        }
    }
}
