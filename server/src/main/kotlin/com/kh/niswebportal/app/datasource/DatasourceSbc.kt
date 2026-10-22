package com.kh.niswebportal.app.datasource

import com.kh.niswebportal.app.approval.ApprovalEbc
import com.kh.niswebportal.configuration.loader.AuthContext
import com.kh.niswebportal.core.builder.buildMData
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.math.ceil

enum class UploadMode { REPLACE, APPEND, UPSERT }

@Suppress("UNCHECKED_CAST")
@Service
class DatasourceSbc(
    private val datasourceEbc: DatasourceEbc,
    private val approvalEbc: ApprovalEbc,
    inquiryStrategies: List<NisInquiryStrategy>,
    migratorStrategies: List<NisMigratorStrategy>,
    private val excelToCsvConverter: ExcelToCsvConverter,
    private val jdbc: JdbcTemplate,
    @Value("\${app.upload.dir:uploads}") private val uploadDirRaw: String,
    @Value("\${app.datasource.schema:public}") private val dbSchema: String,
) {
    private val inquiryMap = inquiryStrategies.associateBy { it.sourceId.lowercase() }
    private val migratorMap = migratorStrategies.associateBy { it.sourceId.lowercase() }

    companion object {
        private const val SNAPSHOT_BATCH_SIZE = 10_000
        private const val PIPE_BUFFER_BYTES = 2 * 1024 * 1024
        private const val UTF8_BOM = '\uFEFF'
        private const val MAX_VALIDATION_ERRORS = 50
        private val SAFE_IDENT_REGEX = Regex("^[a-z_][a-z0-9_]*$")
        private val EXCLUDED_COLUMNS = setOf("id", "created_at", "updated_at")
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HHmmssSSS")
    }

    private val uploadDir: File by lazy {
        val base = if (uploadDirRaw.isBlank() || uploadDirRaw == "uploads") {
            File(System.getProperty("user.dir"), "uploads")
        } else {
            val file = File(uploadDirRaw)
            if (file.isAbsolute) {
                file
            } else {
                File(System.getProperty("user.dir"), uploadDirRaw)
            }
        }

        base.apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val snapshotDir: File by lazy {
        File(uploadDir, "snapshots").also { it.mkdirs() }
    }

    fun listSources(request: MData): MData {
        val page = request.getInt("page") ?: 0
        val size = request.getInt("size") ?: 10
        val list = datasourceEbc.listSources(request)
        val total = datasourceEbc.countSources(request)
        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun listRecentSources(request: MData): MData {
        val page = request.getInt("page") ?: 0
        val size = request.getInt("size") ?: 10
        val list = datasourceEbc.retrieveRecentSource(request)

        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
        }
    }

    fun listSourcesByName(request: MData): MData {
        val page = request.getInt("page") ?: 0
        val size = request.getInt("size") ?: 10
        val list = datasourceEbc.retrieveSourceByName(request)

        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
        }
    }

    fun retrieveSource(sourceId: String): MData {
        return datasourceEbc.retrieveSource(buildMData {
            setString("sourceId", sourceId)
        }) ?: throw IllegalArgumentException("Source not found: $sourceId")
    }

    fun previewSourceData(sourceId: String, uploadId: String?, page: Int, size: Int): MData {
        val strategy = inquiryMap[sourceId.lowercase()]
            ?: throw IllegalArgumentException("No inquiry strategy found for source ID: $sourceId")

        // Public dataset inquiry queries the whole dataset of approved records (uploadId = null)
        val res = strategy.getDynamicReport(uploadId = null)

        val headers = res.tb_headers
        val allRows = res.tb_rows
        val total = allRows.size

        val validPage = if (page <= 0) 1 else page
        val validSize = if (size <= 0) total.coerceAtLeast(10) else size

        val fromIndex = ((validPage - 1) * validSize).coerceAtLeast(0)
        val toIndex = (fromIndex + validSize).coerceAtMost(total)

        val pagedRows = if (fromIndex < total) {
            allRows.subList(fromIndex, toIndex)
        } else {
            emptyList()
        }

        return buildMData {
            set("header", headers)
            set("list", pagedRows)
            set("total", total)
            set("totalPages", if (validSize > 0) ceil(total.toDouble() / validSize).toInt() else 1)
            set("page", validPage)
            set("size", validSize)
        }
    }

    /**
     * Retrieves data for [identifier] (which can be a sourceId or an uploadId),
     * generates an Excel (.xlsx) file, saves it into the upload directory, and returns the generated File for streaming/downloading.
     */
    fun exportDatasourceToExcel(identifier: String): File {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Identifier cannot be blank")
        }

        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
        }

        var sourceId = trimmed.lowercase()
        var targetUploadId: String? = null

        var strategy = inquiryMap[sourceId]

        if (strategy == null) {
            val history = runCatching {
                approvalEbc.retrieveApprovalDetail(buildMData { setString("uploadId", trimmed) })
            }.getOrNull()

            if (history != null && history.getString("sourceId") != null) {
                sourceId = history.getString("sourceId")!!.lowercase()
                targetUploadId = trimmed
                strategy = inquiryMap[sourceId]
            }
        }

        val (sheetTitle, headers, rows) = if (strategy != null) {
            val report = runCatching {
                strategy.getDynamicReport(uploadId = targetUploadId)
            }.getOrElse { ex ->
                throw IllegalStateException("Failed to query dynamic report for source '$sourceId': ${ex.message}", ex)
            }

            val sourceTitle = report.title?.ifBlank { null } ?: sourceId
            Triple(sourceTitle, report.tb_headers, report.tb_rows)
        } else {
            val sourceRecord = datasourceEbc.retrieveSource(buildMData {
                setString("sourceId", sourceId)
            }) ?: throw IllegalArgumentException("Datasource '$sourceId' does not exist")

            val rawTableName = sourceRecord.getString("tableName")?.takeIf { it.isNotBlank() }
                ?: sourceId
            val tableName = resolveSafeTableName(rawTableName)
            val columns = fetchTableColumns(tableName)
            if (columns.isEmpty()) {
                throw IllegalStateException("Table '$tableName' has no columns or does not exist")
            }

            val safeTable = qualifiedTable(tableName)
            val colList = columns.joinToString(", ") { quoteIdent(it) }
            val hasUploadIdCol = columns.any { it.equals("upload_id", ignoreCase = true) }
            val querySql = if (hasUploadIdCol) {
                if (targetUploadId != null) {
                    "SELECT $colList FROM $safeTable WHERE upload_id = ?"
                } else {
                    "SELECT $colList FROM $safeTable WHERE upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01')"
                }
            } else {
                "SELECT $colList FROM $safeTable"
            }

            val dbRows = runCatching {
                if (hasUploadIdCol && targetUploadId != null) {
                    jdbc.queryForList(querySql, targetUploadId)
                } else {
                    jdbc.queryForList(querySql)
                }
            }.getOrElse { ex ->
                throw IllegalStateException("Database query failed on table '$tableName': ${ex.message}", ex)
            }

            val headerItems = listOf(columns.map { HeaderItem(title = humanizeColumn(it)) })
            val dataRows = dbRows.map { rowMap ->
                columns.map { col -> rowMap[col] }
            }

            val sourceName = sourceRecord.getString("sourceName")?.ifBlank { null } ?: sourceId
            Triple(sourceName, headerItems, dataRows)
        }

        val fileName = "${trimmed}.xlsx"
        val destinationFile = File(uploadDir, fileName)

        return runCatching {
            ExcelExportHelper.createExcelFile(
                destinationFile = destinationFile,
                sheetTitle = sheetTitle,
                headers = headers,
                rows = rows
            )
        }.getOrElse { ex ->
            throw IllegalStateException("Failed to generate Excel file for '$trimmed': ${ex.message}", ex)
        }
    }

    @Transactional
    fun createAndUpload(
        file: MultipartFile, sourceId: String, sourceName: String,
        ownerId: String?, mode: UploadMode = UploadMode.REPLACE,
        conflictColumn: String? = null, description: String
    ): MData {
        val actor = ownerId?.takeIf { it.isNotBlank() } ?: AuthContext.userId()

        datasourceEbc.retrieveSource(buildMData {
            setString("sourceId", sourceId)
        })

        val uploadId = "${sourceId}_${UUID.randomUUID()}"

        // 1. Save original Excel file to /uploads directory
        val savedExcelFile = excelToCsvConverter.saveFileToUploads(file, uploadId)

        // Automatically convert to CSV if an Excel file is provided
        val fileToProcess = excelToCsvConverter.convertToCsvMultipartFile(file)

        // 2. Lookup and execute the appropriate migrator strategy
        val migrator = migratorMap[sourceId.lowercase()]
            ?: throw IllegalArgumentException("No migrator found for source ID: $sourceId")

        migrator.processAndMigrate(fileToProcess, uploadId)

        // 3. Register source execution history in PENDING_APPROVAL status ('03')
        datasourceEbc.registerDatasourceHistory(buildMData {
            setString("sourceId", sourceId)
            setString("uploadId", uploadId)
            setString("rawData", "{}")
            setString("filePath", savedExcelFile.path)
            setString("uploadFileName", sourceName)
            setString("description", description)
            setString("firstRegisterUser", actor)
        })

        // 4. Record SUBMITTED audit log
        approvalEbc.registerApprovalAudit(buildMData {
            setString("uploadId", uploadId)
            setString("sourceId", sourceId)
            setString("action", "SUBMITTED")
            setString("actionBy", actor)
            setString("firstRegisterDate", LocalDate.now().format(DATE_FMT))
            setString("firstRegisterTime", LocalTime.now().format(TIME_FMT))
            setString("previousStatus", "")
            setString("newStatus", "03")
            setString("reason", description.ifBlank { "Uploaded file: $sourceName" })
        })

        return buildMData {
            setString("uploadId", uploadId)
            setString("sourceId", sourceId)
            setString("status", "03")
            setString("statusLabel", "PENDING_APPROVAL")
        }
    }

    @Transactional
    fun updateSource(
        sourceId: String, sourceName: String, ownerId: String?,
        file: MultipartFile?, mode: UploadMode = UploadMode.REPLACE,
        conflictColumn: String? = null,
    ): MData {
        // 1. Verify source existence
        datasourceEbc.retrieveSource(buildMData { setString("sourceId", sourceId) })
            ?: throw IllegalArgumentException("Source not found: $sourceId")

        // 2. Update metadata
        datasourceEbc.updateSource(buildMData {
            setString("sourceId", sourceId)
            setString("sourceName", sourceName)
            setString("ownerId", ownerId)
        })

        // 3. Return early if no file provided
        if (file == null) {
            return buildMData {
                setString("sourceId", sourceId)
                setString("sourceName", sourceName)
                setString("ownerId", ownerId)
                set("dataReloaded", false)
            }
        }

        val uploadId = "${sourceId}_${UUID.randomUUID()}"
        val actor = ownerId?.takeIf { it.isNotBlank() } ?: AuthContext.userId()

        // 4. Check Strategy Map for custom migrator
        val migrator = migratorMap[sourceId.lowercase()]
        if (migrator != null) {
            val fileToProcess = excelToCsvConverter.convertToCsvMultipartFile(file)
            migrator.processAndMigrate(fileToProcess, uploadId)

            return buildMData {
                setString("sourceId", sourceId)
                setString("sourceName", sourceName)
                setString("ownerId", ownerId)
                set("dataReloaded", true)
                set("migrated", true)
            }
        }

        // 5. Generic Dynamic CSV/Excel Upload (Fallback if no custom migrator exists)
        val tableName = resolveSafeTableName(sourceId)
        val dbColumns = fetchTableColumns(tableName)
        if (dbColumns.isEmpty()) throw IllegalStateException("Table '$tableName' has no columns or does not exist")

        val schema = DatasourceRegistry.get(sourceId) ?: UnvalidatedSchema(sourceId, dbColumns.size)
        if (dbColumns.size < schema.totalColumns) {
            throw IllegalStateException(
                "Schema '${schema.sourceId}' expects ${schema.totalColumns} columns but table '$tableName' has only ${dbColumns.size}"
            )
        }

        val uploadResult = processUpload(file, UUID.randomUUID().toString(), sourceId, tableName, schema, mode, conflictColumn)

        return buildMData {
            setString("sourceId", sourceId)
            setString("sourceName", sourceName)
            setString("ownerId", ownerId)
            set("dataReloaded", true)
            set("uploadId", uploadResult.getString("uploadId"))
            set("totalRows", uploadResult.getInt("totalRows"))
        }
    }

    fun deleteSource(sourceId: String): MData {
        val param = buildMData { setString("sourceId", sourceId) }
        datasourceEbc.retrieveSource(param)
            ?: throw IllegalArgumentException("Source not found: $sourceId")
        datasourceEbc.deleteSource(param)
        return buildMData {
            setString("sourceId", sourceId)
            set("deleted", true)
        }
    }

    @Transactional
    fun processUpload(
        file: MultipartFile, uploadId: String, sourceId: String,
        tableName: String, schema: SourceSchema,
        mode: UploadMode, conflictColumn: String? = null,
    ): MData {
        val tableColumns = fetchTableColumns(tableName)

        val normalizedConflict: String? = if (mode == UploadMode.UPSERT) {
            if (conflictColumn.isNullOrBlank()) {
                throw IllegalArgumentException("conflictColumn is required when mode is UPSERT")
            }
            val n = normalizeIdent(conflictColumn)
            requireSafeIdent(n)
            if (n !in tableColumns) {
                throw IllegalArgumentException("conflictColumn '$conflictColumn' is not one of the target table columns")
            }
            n
        } else null

        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val shortId  = uploadId.replace("-", "").take(8)
        val tempFile = File(uploadDir, "upload_${sourceId}_${timestamp}_${shortId}.csv")

        try {
            file.inputStream.use { input ->
                java.nio.file.Files.copy(input, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }

            var totalRows = 0

            tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
                val rawHeader = reader.readLine()?.let { stripBom(it) }
                    ?: throw IllegalArgumentException("CSV file is empty")
                val headerFields = parseCsvLine(rawHeader)
                if (headerFields.size < tableColumns.size) {
                    throw IllegalArgumentException(
                        "CSV has ${headerFields.size} columns but table '$tableName' has ${tableColumns.size}"
                    )
                }
                reader.lineSequence().forEach { line ->
                    if (line.isNotBlank()) totalRows++
                }
            }

            val rowsBeforeUpload = countTableRows(tableName)

            val snapshotPath = if (mode == UploadMode.APPEND) {
                snapshotExistingData(sourceId, uploadId, tableName, fetchTableColumns(tableName))
            } else {
                null
            }

            when (mode) {
                UploadMode.REPLACE -> {
                    truncateTargetTable(tableName)
                    tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readLine()
                        streamCopyInsert(qualifiedTable(tableName), tableColumns, schema, reader)
                    }
                }
                UploadMode.APPEND,
                UploadMode.UPSERT -> {
                    tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readLine()
                        stagingCopyInsert(
                            targetTable    = tableName,
                            tableColumns   = tableColumns,
                            schema         = schema,
                            reader         = reader,
                            mode           = mode,
                            conflictColumn = normalizedConflict,
                        )
                    }
                }
            }

            datasourceEbc.registerDatasourceHistory(buildMData {
                setString("sourceId", sourceId)
                setString("uploadId", uploadId)
                setString("rawData", "{}")
                setString("filePath", tempFile.path)
            })

            val rowsAfterUpload = countTableRows(tableName)

            return buildMData {
                setString("uploadId", uploadId)
                setString("sourceId", sourceId)
                setString("tableName", tableName)
                setString("mode", mode.name)
                setInt("rowsInserted", totalRows)
                set("rowsBefore", rowsBeforeUpload)
                set("rowsAfter", rowsAfterUpload)
                set("tableColumns", tableColumns)
                set("async", false)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun streamCopyInsert(
        copyTarget: String,
        tableColumns: List<String>,
        schema: SourceSchema,
        reader: java.io.BufferedReader,
    ) {
        tableColumns.forEach { requireSafeIdent(it) }

        val lines = reader.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return

        var headerLineIndex = -1
        var headerFields = emptyList<String>()

        for ((idx, line) in lines.withIndex()) {
            val fields = parseCsvLine(stripBom(line)).map { it.lowercase().trim() }
            if (tableColumns.any { it.lowercase() in fields }) {
                headerLineIndex = idx
                headerFields = fields
                break
            }
        }

        val csvHeaderMap = if (headerLineIndex != -1) {
            tableColumns.map { dbCol -> headerFields.indexOf(dbCol.lowercase()) }
        } else {
            tableColumns.indices.toList()
        }

        val columnCount = tableColumns.size
        val colList      = tableColumns.joinToString(", ") { quoteIdent(it) }
        val copySql      = "COPY $copyTarget ($colList) FROM STDIN WITH (FORMAT csv, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL '')"

        val errors        = mutableListOf<String>()
        var writerError: Throwable? = null
        val pipedOut      = PipedOutputStream()
        val pipedIn       = PipedInputStream(pipedOut, PIPE_BUFFER_BYTES)

        val writerThread = Thread {
            runCatching {
                pipedOut.bufferedWriter(Charsets.UTF_8).use { bw ->
                    var rowNum = 0
                    val dataLines = if (headerLineIndex != -1) lines.drop(headerLineIndex + 1) else lines

                    dataLines.forEach { line ->
                        rowNum++
                        val rawCsvFields = parseCsvLine(line)
                        val out = ArrayList<String>(columnCount)

                        for (i in 0 until columnCount) {
                            val dbCol = tableColumns[i]
                            val csvIdx = csvHeaderMap[i]

                            val rawValue = if (csvIdx in rawCsvFields.indices) {
                                convertKhmerNumerals(rawCsvFields[csvIdx])
                            } else {
                                ""
                            }

                            val rule = if (i < schema.rulesByIndex.size) schema.rulesByIndex[i] else null
                            if (rule != null) {
                                val err = FieldValidators.validate(rule, rawValue)
                                if (err != null && errors.size < MAX_VALIDATION_ERRORS) {
                                    errors.add("row $rowNum col ${i + 1} ($dbCol): $err")
                                }
                            }
                            out.add(rawValue.trim())
                        }

                        if (errors.isNotEmpty()) return@use
                        bw.write(out.joinToString(",") { escapeCsvField(it) })
                        bw.newLine()
                    }
                }
            }.onFailure {
                writerError = it
                runCatching { pipedOut.close() }
            }
        }.also {
            it.isDaemon = true
            it.name     = "csv-copy-writer-$copyTarget"
            it.start()
        }

        runCatching {
            jdbc.execute(ConnectionCallback<Unit> { con ->
                val pgConn      = con.unwrap(BaseConnection::class.java)
                val copyManager = CopyManager(pgConn)
                @Suppress("SqlSourceToSinkFlow")
                copyManager.copyIn(copySql, pipedIn)
            })
        }.onFailure { copyError ->
            writerThread.interrupt()
            writerThread.join(3_000)
            if (errors.isNotEmpty()) {
                throw IllegalArgumentException("CSV validation failed:\n${errors.joinToString("\n")}")
            }
            throw copyError
        }

        writerThread.join()
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("CSV validation failed:\n${errors.joinToString("\n")}")
        }
        writerError?.let { throw IllegalStateException("CSV stream failed mid-upload", it) }
    }

    private fun stagingCopyInsert(
        targetTable: String, tableColumns: List<String>, schema: SourceSchema,
        reader: java.io.BufferedReader, mode: UploadMode, conflictColumn: String?,
    ) {
        val stagingTable = "stg_${targetTable}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        requireSafeIdent(stagingTable)

        try {
            @Suppress("SqlSourceToSinkFlow")
            jdbc.execute("CREATE TEMP TABLE ${quoteIdent(stagingTable)} (LIKE ${qualifiedTable(targetTable)} INCLUDING DEFAULTS)")

            streamCopyInsert(quoteIdent(stagingTable), tableColumns, schema, reader)

            val colList      = tableColumns.joinToString(", ") { quoteIdent(it) }
            val stagingAlias = tableColumns.joinToString(", ") { "s.${quoteIdent(it)}" }

            when (mode) {
                UploadMode.APPEND -> {
                    @Suppress("SqlSourceToSinkFlow")
                    jdbc.execute(
                        """
                        INSERT INTO ${qualifiedTable(targetTable)} ($colList)
                        SELECT $stagingAlias
                        FROM   ${quoteIdent(stagingTable)} s
                        """.trimIndent()
                    )
                }
                UploadMode.UPSERT -> {
                    val conflictCol   = quoteIdent(conflictColumn!!)
                    val updateSetCols = tableColumns
                        .filter { it != conflictColumn }
                        .joinToString(",\n               ") { "${quoteIdent(it)} = EXCLUDED.${quoteIdent(it)}" }

                    @Suppress("SqlSourceToSinkFlow")
                    jdbc.execute(
                        """
                        INSERT INTO ${qualifiedTable(targetTable)} ($colList)
                        SELECT $stagingAlias
                        FROM   ${quoteIdent(stagingTable)} s
                        ON CONFLICT ($conflictCol)
                        DO UPDATE SET
                                   $updateSetCols
                        """.trimIndent()
                    )
                }
                else -> {}
            }
        } finally {
            runCatching {
                @Suppress("SqlSourceToSinkFlow")
                jdbc.execute("DROP TABLE IF EXISTS ${quoteIdent(stagingTable)}")
            }
        }
    }

    private fun countTableRows(tableName: String): Long =
        runCatching {
            @Suppress("SqlSourceToSinkFlow")
            jdbc.queryForObject("SELECT COUNT(*) FROM ${qualifiedTable(tableName)}", Long::class.java) ?: 0L
        }.getOrDefault(0L)

    private fun humanizeColumn(raw: String): String =
        raw.split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private fun stripBom(line: String): String =
        if (line.isNotEmpty() && line[0] == UTF8_BOM) line.substring(1) else line

    private fun convertKhmerNumerals(input: String): String {
        return input.map { ch ->
            when (ch) {
                '\u17e0' -> '0'
                '\u17e1' -> '1'
                '\u17e2' -> '2'
                '\u17e3' -> '3'
                '\u17e4' -> '4'
                '\u17e5' -> '5'
                '\u17e6' -> '6'
                '\u17e7' -> '7'
                '\u17e8' -> '8'
                '\u17e9' -> '9'
                else -> ch
            }
        }.joinToString("")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result  = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var i       = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuote                                              -> inQuote = true
                c == '"' && inQuote && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' && inQuote                                               -> inQuote = false
                c == ',' && !inQuote                                              -> { result.add(current.toString().trim()); current.clear() }
                else                                                              -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun escapeCsvField(value: String): String {
        if (value.isEmpty()) return ""
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun snapshotExistingData(
        sourceId: String, uploadId: String, tableName: String, dbColumns: List<String>,
    ): String? {
        val safeTable     = qualifiedTable(tableName)
        val colList       = dbColumns.joinToString(", ") { quoteIdent(it) }
        val existingCount = runCatching {
            @Suppress("SqlSourceToSinkFlow")
            jdbc.queryForObject("SELECT COUNT(*) FROM $safeTable", Long::class.java) ?: 0L
        }.getOrDefault(0L)

        if (existingCount == 0L) return null

        val timestamp    = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val shortId      = uploadId.replace("-", "").take(8)
        val fileName     = "snapshot_${sourceId}_${timestamp}_${shortId}.csv.gz"
        val snapshotFile = File(snapshotDir, fileName)
        val pageSql      = "SELECT $colList FROM $safeTable LIMIT ? OFFSET ?"

        GZIPOutputStream(FileOutputStream(snapshotFile)).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(dbColumns.joinToString(","))
            writer.newLine()
            var offset = 0
            while (true) {
                @Suppress("SqlSourceToSinkFlow")
                val page = jdbc.queryForList(pageSql, SNAPSHOT_BATCH_SIZE, offset)
                if (page.isEmpty()) break
                page.forEach { rowMap ->
                    writer.write(dbColumns.joinToString(",") { col ->
                        rowMap[col]?.toString()?.replace(",", " ") ?: ""
                    })
                    writer.newLine()
                }
                offset += SNAPSHOT_BATCH_SIZE
            }
        }

        return snapshotFile.absolutePath
    }

    private fun truncateTargetTable(tableName: String) {
        @Suppress("SqlSourceToSinkFlow")
        jdbc.execute("TRUNCATE TABLE ${qualifiedTable(tableName)}")
    }

    private fun fetchTableColumns(tableName: String): List<String> =
        (jdbc.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name   = LOWER(?)
            ORDER BY ordinal_position
            """.trimIndent(),
            String::class.java, dbSchema, tableName,
        ) as List<String>)
            .filter { it.lowercase() !in EXCLUDED_COLUMNS }

    private fun resolveSafeTableName(tableName: String): String {
        val candidate = normalizeIdent(tableName)
        requireSafeIdent(candidate)
        val exists = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_name   = ?
            """.trimIndent(),
            Int::class.java, dbSchema, candidate,
        ) ?: 0
        if (exists == 0) throw IllegalArgumentException("Table '$candidate' does not exist in schema '$dbSchema'")
        return candidate
    }

    private fun normalizeIdent(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")

    private fun requireSafeIdent(ident: String) {
        if (!SAFE_IDENT_REGEX.matches(ident) || ident.length > 63)
            throw IllegalArgumentException("Unsafe identifier: '$ident'")
    }

    private fun quoteIdent(ident: String): String {
        requireSafeIdent(ident)
        return "\"$ident\""
    }

    fun getSheetNames(file: MultipartFile): List<String> {
        file.inputStream.use { inputStream ->
            WorkbookFactory.create(inputStream).use { workbook ->
                return (0 until workbook.numberOfSheets)
                    .map { workbook.getSheetName(it) }
            }
        }
    }

    private fun qualifiedTable(tableName: String): String =
        "${quoteIdent(dbSchema)}.${quoteIdent(tableName)}"

    fun retrieveDatasourceByCategory(request: MData): MData {
        val page = request.getInt("page") ?: 1
        val size = request.getInt("size") ?: 10
        val list = datasourceEbc.retrieveDatasourceByCategory(request)
        val total = datasourceEbc.countDatasourceByCategory(request)
        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", if (size > 0) ceil(total.toDouble() / size).toInt() else 1)
        }
    }

    fun retrieveSourceByCategory(request: MData): MData {
        val page = request.getInt("page") ?: 1
        val size = request.getInt("size") ?: 10
        val list = datasourceEbc.retrieveSourceByCategory(request)
        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", list.size)
            set("totalPages", if (size > 0) ceil(list.size.toDouble() / size).toInt() else 1)
        }
    }

    fun retrieveDatasourceManagementPage(request: MData): MData {
        val page = request.getInt("page") ?: 1
        val size = request.getInt("size") ?: 10
        val list = datasourceEbc.retrieveDatasourceManagementPage(request)
        val total = datasourceEbc.countDatasourceManagementPage(request)
        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", if (size > 0) ceil(total.toDouble() / size).toInt() else 1)
        }
    }

    fun retrieveDatasourceHistorySummaryForDashBoardPage(): MData {
        val res = datasourceEbc.retrieveDatasourceHistorySummaryForDashBoardPage(buildMData { })
        return buildMData {
            setInt("totalRecords", res.getInt("totalRecords"))
            setInt("totalPublished", res.getInt("totalPublished"))
            setInt("publishedThisMonth", res.getInt("publishedThisMonth"))
        }
    }
}
