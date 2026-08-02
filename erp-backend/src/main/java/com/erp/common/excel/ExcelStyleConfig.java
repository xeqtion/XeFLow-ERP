package com.erp.common.excel;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Excel export style options used by {@link ExcelUtil}.
 */
public class ExcelStyleConfig {

    private String title;
    private short headerBgColor = IndexedColors.GREY_25_PERCENT.getIndex();
    private short headerFontColor = IndexedColors.BLACK.getIndex();
    private short headerFontSize = 11;
    private boolean headerBold = true;
    private HorizontalAlignment headerHorizontalAlignment = HorizontalAlignment.CENTER;
    private short contentFontSize = 11;
    private boolean showBorder = true;
    private Set<Integer> mergeColumnIndices = Collections.emptySet();

    public static Builder builder() {
        return new Builder();
    }

    public String getTitle() {
        return title;
    }

    public short getHeaderBgColor() {
        return headerBgColor;
    }

    public short getHeaderFontColor() {
        return headerFontColor;
    }

    public short getHeaderFontSize() {
        return headerFontSize;
    }

    public boolean isHeaderBold() {
        return headerBold;
    }

    public HorizontalAlignment getHeaderHorizontalAlignment() {
        return headerHorizontalAlignment;
    }

    public short getContentFontSize() {
        return contentFontSize;
    }

    public boolean isShowBorder() {
        return showBorder;
    }

    public Set<Integer> getMergeColumnIndices() {
        return mergeColumnIndices;
    }

    public static class Builder {
        private final ExcelStyleConfig config = new ExcelStyleConfig();

        public Builder title(String title) {
            config.title = title;
            return this;
        }

        public Builder headerBgColor(short headerBgColor) {
            config.headerBgColor = headerBgColor;
            return this;
        }

        public Builder headerFontColor(short headerFontColor) {
            config.headerFontColor = headerFontColor;
            return this;
        }

        public Builder headerFontSize(short headerFontSize) {
            config.headerFontSize = headerFontSize;
            return this;
        }

        public Builder headerBold(boolean headerBold) {
            config.headerBold = headerBold;
            return this;
        }

        public Builder headerHorizontalAlignment(HorizontalAlignment headerHorizontalAlignment) {
            config.headerHorizontalAlignment = headerHorizontalAlignment;
            return this;
        }

        public Builder contentFontSize(short contentFontSize) {
            config.contentFontSize = contentFontSize;
            return this;
        }

        public Builder showBorder(boolean showBorder) {
            config.showBorder = showBorder;
            return this;
        }

        public Builder mergeColumnIndices(Set<Integer> mergeColumnIndices) {
            config.mergeColumnIndices = mergeColumnIndices == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new HashSet<>(mergeColumnIndices));
            return this;
        }

        public ExcelStyleConfig build() {
            return config;
        }
    }
}
