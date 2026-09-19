package com.hanserwei.hanmenu.reporting.domain;

/** 将已经读取完毕的统计快照导出为工作簿，不依赖 HTTP、数据库或其他业务模块. */
public interface ReportExporter {
  /** 返回标准 XLSX 字节，金额必须保留精确十进制值且不把商品名称解释成公式. */
  byte[] export(ReportWorkbook report);
}
