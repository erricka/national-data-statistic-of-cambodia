package com.kh.niswebportal.app.datasource

import com.kh.niswebportal.core.util.ByteArrayMultipartFile
import jakarta.annotation.PostConstruct
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Component
class ExcelToCsvConverter(
    @Value("\${app.upload.dir:uploads}") private val uploadDirConfig: String,
) {

    data class FileInfoResponse(
        val title: String,
        val description: String,
        val uploadId: String?,
        val fileName: String,
        val fileSize: Long,        // File size in bytes
        val formattedSize: String, // Human-readable size (e.g., "1.25 MB")
        val extension: String
    )

    private lateinit var uploadDir: Path

    @PostConstruct
    fun init() {
        uploadDir = Paths.get(uploadDirConfig).toAbsolutePath().normalize()
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir)
        }
    }

    /**
     * Saves the uploaded file to disk using uploadId as the file name.
     */
    fun saveFileToUploads(file: MultipartFile, uploadId: String): File {
        val extension = file.originalFilename?.substringAfterLast(".", "")
            ?.let { if (it.isNotEmpty()) ".$it" else "" } ?: ""

        val fileName = "$uploadId$extension"
        val destinationPath = uploadDir.resolve(fileName)

        // Stream bytes directly to avoid Tomcat temporary file transfer issues
        file.inputStream.use { inputStream ->
            Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING)
        }

        return destinationPath.toFile()
    }

    /**
     * Checks if the file is an Excel spreadsheet (.xlsx or .xls).
     */
    fun isExcelFile(file: MultipartFile): Boolean {
        val filename = file.originalFilename ?: return false
        return filename.endsWith(".xlsx", ignoreCase = true) ||
                filename.endsWith(".xls", ignoreCase = true)
    }

    /**
     * Converts an Excel file (or MultipartFile) into a CSV MultipartFile.
     */
    fun convertToCsvMultipartFile(file: MultipartFile, sheetIndex: Int = 0): MultipartFile {
        if (!isExcelFile(file)) {
            return file
        }

        val outputStream = ByteArrayOutputStream()

        file.inputStream.use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(sheetIndex)
            val dataFormatter = DataFormatter()
            val evaluator = workbook.creationHelper.createFormulaEvaluator()

            // Step 1: Find the maximum number of columns to keep the CSV grid uniform
            var maxColNum = 0
            for (i in 0..sheet.lastRowNum) {
                val r = sheet.getRow(i)
                if (r != null && r.lastCellNum > maxColNum) {
                    maxColNum = r.lastCellNum.toInt()
                }
            }

            outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                // Step 2: Iterate by exact index to PRESERVE empty rows
                for (rowIndex in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex)
                    val cellValues = mutableListOf<String>()

                    for (colIndex in 0 until maxColNum) {
                        if (row == null) {
                            // If the row is empty, write empty cells
                            cellValues.add("")
                        } else {
                            // Safely get the cell, treating missing cells as blank
                            val cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                            val formattedValue = dataFormatter.formatCellValue(cell, evaluator)
                            cellValues.add(escapeCsvValue(formattedValue))
                        }
                    }

                    writer.write(cellValues.joinToString(","))
                    writer.newLine()
                }
            }
            workbook.close()
        }

        val csvBytes = outputStream.toByteArray()
        val originalFilename = file.originalFilename ?: "file.xlsx"
        val csvFilename = originalFilename.substringBeforeLast(".") + ".csv"

        return ByteArrayMultipartFile(
            name = file.name,
            originalFilename = csvFilename,
            contentType = "text/csv",
            content = csvBytes
        )
    }

    private fun escapeCsvValue(value: String): String {
        var escaped = value.replace("\"", "\"\"")
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
            escaped = "\"$escaped\""
        }
        return escaped
    }

    /**
     * Retrieves file details from the /uploads directory by uploadId or sourceId.
     */
    fun getFileInfoBySourceId(uploadId: String): FileInfoResponse? {
        val targetFile = Files.list(uploadDir).use { stream ->
            stream.filter { path ->
                val fileNameWithoutExt = path.fileName.toString().substringBeforeLast(".")
                fileNameWithoutExt.equals(uploadId, ignoreCase = true)
            }.findFirst().orElse(null)?.toFile()
        } ?: return null

        return getFileInfoByFile(uploadId, targetFile)
    }

    /**
     * Builds FileInfoResponse directly from a File on disk.
     */
    fun getFileInfoByFile(uploadId: String, file: File): FileInfoResponse {
        val fileName = file.name
        val fileSize = file.length()
        val extension = fileName.substringAfterLast(".", "")

        return FileInfoResponse(
            title = fileName,
            description = "",
            uploadId = uploadId,
            fileName = fileName,
            fileSize = fileSize,
            formattedSize = formatFileSize(fileSize),
            extension = if (extension.isNotEmpty()) ".$extension" else ""
        )
    }

    /**
     * Resolves the underlying file object from /uploads by sourceId.
     */
    fun getFileBySourceId(sourceId: String): File? {
        return Files.list(uploadDir).use { stream ->
            stream.filter { path ->
                val fileNameWithoutExt = path.fileName.toString().substringBeforeLast(".")
                fileNameWithoutExt.equals(sourceId, ignoreCase = true)
            }.findFirst().orElse(null)?.toFile()
        }
    }

    /**
     * Utility to format file size into B, KB, MB, or GB.
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = String.format("%.2f", size / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }
}
