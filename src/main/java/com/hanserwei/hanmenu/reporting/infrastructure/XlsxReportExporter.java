package com.hanserwei.hanmenu.reporting.infrastructure;

import com.hanserwei.hanmenu.reporting.domain.BusinessTime;
import com.hanserwei.hanmenu.reporting.domain.ReportExporter;
import com.hanserwei.hanmenu.reporting.domain.ReportWorkbook;
import com.hanserwei.hanmenu.reporting.domain.ReportingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 生成有界 XLSX；金额用十进制文本保真，外部商品名称始终写入字符串单元格. */
@Component
@Transactional(propagation = Propagation.NEVER)
public class XlsxReportExporter implements ReportExporter {
  @Override
  public byte[] export(ReportWorkbook report) {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var header = workbook.createCellStyle();
      var font = workbook.createFont();
      font.setBold(true);
      font.setColor(IndexedColors.WHITE.getIndex());
      header.setFont(font);
      header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
      header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      var summary = workbook.createSheet("口径与汇总");
      summary.setColumnWidth(0, 38 * 256);
      summary.setColumnWidth(1, 90 * 256);
      row(summary, 0, header, "项目", "值");
      var sum = report.summary();
      Object[][] values = {
        {"经营区间", report.period().from() + " 至 " + report.period().to()},
        {"时区", BusinessTime.ZONE.getId()},
        {"币种", "CNY"},
        {"投影代际", report.metadata().generation()},
        {"投影修订号", report.metadata().revision()},
        {"投影更新时间", String.valueOf(report.metadata().updatedAt())},
        {"金额格式", "精确十进制文本，汇总由服务端计算；商品名称不会转换为公式"},
        {"营业额口径", "按完成日统计当前已完成订单的成交金额"},
        {"资金口径", "渠道付款日收款减退款确认日支出；与营业额不是同一指标"},
        {"完成率口径", "区间内创建订单群组中已完成数量 / 该群组总数量"},
        {"新增订单", sum.submitted()},
        {"完成订单", sum.completed()},
        {"已完成营业额", sum.turnover()},
        {"已确认收款", sum.receipts()},
        {"已确认退款", sum.refunds()},
        {"净收款", sum.netReceipts()},
        {"完成率百分比", sum.completionRate()},
        {"平均已完成订单金额", sum.averageOrderValue()},
        {"新增顾客", sum.newCustomers()},
        {"期末累计顾客", sum.totalCustomers()}
      };
      for (int index = 0; index < values.length; index++) {
        row(summary, index + 1, null, values[index]);
      }
      var daily = workbook.createSheet("经营日账");
      row(
          daily, 0, header, "经营日期", "新增订单", "群组已完成", "群组已取消", "当日完成", "营业额 CNY", "收款 CNY", "退款 CNY",
          "净收款 CNY", "新增顾客", "累计顾客", "完成率 %");
      int index = 1;
      for (var day : report.days()) {
        row(
            daily,
            index++,
            null,
            day.date().toString(),
            day.submitted(),
            day.completedCohort(),
            day.cancelledCohort(),
            day.completed(),
            day.turnover(),
            day.receipts(),
            day.refunds(),
            day.netReceipts(),
            day.newCustomers(),
            day.cumulativeCustomers(),
            day.completionRate());
      }
      table(daily, 12);
      var sales = workbook.createSheet("商品销量");
      row(sales, 0, header, "商品 UUID", "种类", "最近订单快照名称", "完成销量", "成交金额 CNY");
      index = 1;
      for (var item : report.sales()) {
        row(
            sales,
            index++,
            null,
            item.productId().toString(),
            item.kind(),
            item.name(),
            item.quantity(),
            item.amount());
      }
      table(sales, 5);
      sales.setColumnWidth(0, 38 * 256);
      sales.setColumnWidth(2, 35 * 256);
      var checks = workbook.createSheet("资金对账");
      row(checks, 0, header, "指标", "数量");
      var check = report.reconciliation();
      row(checks, 1, null, "收款缺少订单", check.missingOrders());
      row(checks, 2, null, "收款金额或支付引用不匹配", check.paymentMismatches());
      row(checks, 3, null, "退款缺少原支付", check.missingPayments());
      row(checks, 4, null, "退款订单或金额不匹配", check.refundMismatches());
      row(checks, 5, null, "全店当前待退款", check.pendingRefunds());
      row(checks, 6, null, "已付款订单缺少收款事实", check.ordersMissingReceipts());
      row(checks, 7, null, "已退款订单缺少退款事实", check.ordersMissingRefunds());
      table(checks, 2);
      checks.setColumnWidth(0, 45 * 256);
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new ReportingException(ReportingException.Reason.UNAVAILABLE, "报表文件暂时无法生成");
    }
  }

  private void row(
      org.apache.poi.ss.usermodel.Sheet sheet, int index, CellStyle style, Object... values) {
    var row = sheet.createRow(index);
    for (int column = 0; column < values.length; column++) {
      var cell = row.createCell(column);
      Object value = values[column];
      if (value instanceof BigDecimal amount) {
        cell.setCellValue(amount.toPlainString());
      } else if (value instanceof Number number) {
        cell.setCellValue(number.doubleValue());
      } else {
        cell.setCellValue(String.valueOf(value));
      }
      if (style != null) {
        cell.setCellStyle(style);
      }
    }
  }

  private void table(org.apache.poi.ss.usermodel.Sheet sheet, int columns) {
    sheet.createFreezePane(0, 1);
    sheet.setAutoFilter(
        new CellRangeAddress(0, Math.max(1, sheet.getLastRowNum()), 0, columns - 1));
    for (int column = 0; column < columns; column++) {
      sheet.setColumnWidth(column, 18 * 256);
    }
  }
}
