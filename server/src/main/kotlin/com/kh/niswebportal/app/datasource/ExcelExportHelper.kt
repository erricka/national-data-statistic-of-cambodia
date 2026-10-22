package com.kh.niswebportal.app.datasource

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import java.io.File
import java.io.FileOutputStream

object ExcelExportHelper {

    /**
     * Generates an Excel (.xlsx) file using SXSSFWorkbook (streaming for low memory usage).
     *
     * @param destinationFile The target File on disk to write to.
     * @param sheetTitle Title of the sheet.
     * @param headers Multi-level or single-level headers (List of Header rows).
     * @param rows Data rows.
     */
    fun createExcelFile(
        destinationFile: File,
        sheetTitle: String = "Data",
        headers: List<List<HeaderItem>>,
        rows: List<List<Any?>>
    ): File {
        // Ensure parent directory exists
        destinationFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        // SXSSFWorkbook with row window size of 100 for low memory consumption
        SXSSFWorkbook(100).use { workbook ->
            workbook.isCompressTempFiles = true
            val sheetName = sheetTitle.replace(Regex("""[\\/*?:\[\]]"""), "_").take(31).ifBlank { "Data" }
            val sheet = workbook.createSheet(sheetName)

            // Header cell style
            val headerFont = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 11
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                borderBottom = BorderStyle.THIN
                borderTop = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            // Normal data cell style
            val dataStyle = workbook.createCellStyle().apply {
                borderBottom = BorderStyle.THIN
                borderTop = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            var rowIndex = 0

            // 1. Write Header Rows
            for (headerRow in headers) {
                val row = sheet.createRow(rowIndex++)
                headerRow.forEachIndexed { colIndex, item ->
                    val cell = row.createCell(colIndex)
                    cell.setCellValue(item.title)
                    cell.cellStyle = headerStyle
                }
            }

            // 2. Write Data Rows
            for (dataRow in rows) {
                val row = sheet.createRow(rowIndex++)
                dataRow.forEachIndexed { colIndex, value ->
                    val cell = row.createCell(colIndex)
                    cell.cellStyle = dataStyle
                    when (value) {
                        null -> cell.setBlank()
                        is Number -> cell.setCellValue(value.toDouble())
                        is Boolean -> cell.setCellValue(value)
                        else -> cell.setCellValue(value.toString())
                    }
                }
            }

            // 3. Write out to destination file
            FileOutputStream(destinationFile).use { fos ->
                workbook.write(fos)
            }
        }

        return destinationFile
    }
}
