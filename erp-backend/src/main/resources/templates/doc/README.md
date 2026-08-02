# 单据 Word 模板目录

`DocumentService` 会从 `classpath:templates/doc/` 读取 Word 模板（poi-tl）。

命名约定：`<单据>_<语言>.docx`，例如：

- `pi_zh.docx` / `pi_en.docx` —— 形式发票 PI（中/英）
- `customer_zh.docx` —— 客户资料表
- `statement_zh.docx` —— 销售月对账单
- `performance_zh.docx` —— 外贸业绩报告

模板中用 `{{占位符}}` 标记业务字段，渲染时由 `Map<String,Object>` 填充。
（占位符语法详见 poi-tl 文档。）
