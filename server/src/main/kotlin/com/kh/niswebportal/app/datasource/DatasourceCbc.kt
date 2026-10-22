package com.kh.niswebportal.app.datasource

import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.annotation.RequirePermission
import com.kh.niswebportal.core.builder.buildMData
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.FileInputStream

@RestController
class DatasourceCbc(
    private val datasourceSbc: DatasourceSbc,
    private val excelToCsvConverter: ExcelToCsvConverter,
) {
    @NotRequirePermission
    @PostMapping("/DTSI001")
    fun listSources(@RequestBody(required = false) body: MData?): MResponse =
        runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(
                datasourceSbc.listSources(body ?: com.kh.niswebportal.core.builder.buildMData {})
            )
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }

    @NotRequirePermission
    @PostMapping("/DTSI002")
    fun retrieveSource(@RequestBody body: MData): MResponse {
        val sourceId = body.getString("sourceId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("sourceId is required"))
        return runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(datasourceSbc.retrieveSource(sourceId))
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }
    }

    @NotRequirePermission
    @PostMapping("/DTSI003")
    fun previewSourceData(@RequestBody body: MData): MResponse {
        val sourceId = body.getString("sourceId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("sourceId is required"))
        val uploadId = body.getString("upload_id")?.trim()
        return runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(
                datasourceSbc.previewSourceData(sourceId, uploadId, body.getInt("page") ?: 0, body.getInt("size") ?: 50)
            )
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }
    }

    @RequirePermission
    @PostMapping("/DTSR002", consumes = ["multipart/form-data"])
    fun createAndUpload(
        @RequestPart("file")                      file:           MultipartFile,
        @RequestPart("sourceId")                  sourceId:       String,
        @RequestPart("sourceName")                sourceName:     String,
        @RequestPart("ownerId",        required = false) ownerId:        String?,
        @RequestPart("mode",           required = false) mode:           String?,
        @RequestPart("conflictColumn", required = false) conflictColumn: String?,
        @RequestPart("description")                description:     String?
    ): MResponse =
        runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(
                datasourceSbc.createAndUpload(
                    file, sourceId.trim(), sourceName.trim(),
                    ownerId?.trim(), resolveUploadMode(mode),
                    conflictColumn?.trim()?.lowercase(), description.toString().trim()
                )
            )
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }

    @NotRequirePermission
    @PostMapping("/DTSU001", consumes = ["multipart/form-data"])
    fun updateSource(
        @RequestPart("sourceId")                  sourceId:       String,
        @RequestPart("sourceName")                sourceName:     String,
        @RequestPart("ownerId",        required = false) ownerId:        String?,
        @RequestPart("file",           required = false) file:           MultipartFile?,
        @RequestPart("mode",           required = false) mode:           String?,
        @RequestPart("conflictColumn", required = false) conflictColumn: String?,
    ): MResponse =
        runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(
                datasourceSbc.updateSource(
                    sourceId.trim(), sourceName.trim(),
                    ownerId?.trim(), file, resolveUploadMode(mode),
                    conflictColumn?.trim()?.lowercase(),
                )
            )
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }

    @NotRequirePermission
    @PostMapping("/DTSD001")
    fun deleteSource(@RequestBody body: MData): MResponse {
        val sourceId = body.getString("sourceId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("sourceId is required"))
        return runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(datasourceSbc.deleteSource(sourceId))
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }
    }

    private fun resolveUploadMode(mode: String?): UploadMode =
        when (mode?.uppercase()?.trim()) {
            "APPEND"  -> UploadMode.APPEND
            "REPLACE" -> UploadMode.REPLACE
            "UPSERT"  -> UploadMode.UPSERT
            null      -> UploadMode.REPLACE
            else      -> throw IllegalArgumentException("Invalid mode '$mode'. Allowed values: REPLACE, APPEND, UPSERT")
        }

    @NotRequirePermission
    @PostMapping("/DTSI005")
    fun listRecentSources(@RequestBody(required = false) body: MData?): MResponse =
        runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(
                datasourceSbc.listRecentSources(body ?: com.kh.niswebportal.core.builder.buildMData {})
            )
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }

    @NotRequirePermission
    @PostMapping("/DTSI006")
    fun listSourcesByName(@RequestBody(required = false) body: MData?): MResponse =
        runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(
                datasourceSbc.listSourcesByName(body ?: com.kh.niswebportal.core.builder.buildMData {})
            )
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }

    //datasouce by category (show in category detail page)
    @NotRequirePermission
    @PostMapping("/DTSI007")
    fun retrieveDatasourceByCategory(@RequestBody body: MData): MResponse {
        return try {
            val res = datasourceSbc.retrieveDatasourceByCategory(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @GetMapping("/files/{uploadId}")
    fun getFileInfo(@PathVariable uploadId: String): MResponse {
        return try {
            var fileInfo = excelToCsvConverter.getFileInfoBySourceId(uploadId)

            // If file is not found in uploads folder, dynamically generate it and save to uploads folder
            if (fileInfo == null) {
                val generatedFile = runCatching {
                    datasourceSbc.exportDatasourceToExcel(uploadId)
                }.getOrNull()

                if (generatedFile != null && generatedFile.exists()) {
                    fileInfo = excelToCsvConverter.getFileInfoByFile(uploadId, generatedFile)
                }
            }

            if (fileInfo == null) {
                return CommonMessageCode.SUCCESS.generateIMResponse(buildMData { })
            }

            val responseData = buildMData {
                put("uploadId", fileInfo.uploadId)
                put("fileName", fileInfo.fileName)
                put("fileSize", fileInfo.fileSize)
                put("formattedSize", fileInfo.formattedSize)
                put("extension", fileInfo.extension)
            }

            CommonMessageCode.SUCCESS.generateIMResponse(responseData)

        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @GetMapping("/files/{sourceId}/download")
    fun downloadFile(@PathVariable sourceId: String): ResponseEntity<StreamingResponseBody> {
        val file = datasourceSbc.exportDatasourceToExcel(sourceId)

        val streamingResponseBody = StreamingResponseBody { outputStream ->
            FileInputStream(file).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.name}\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(file.length())
            .body(streamingResponseBody)
    }

    //datasouce by category (show in category detail page)
    @NotRequirePermission
    @PostMapping("/DTSI008")
    fun retrieveSourceByCategory(@RequestBody body: MData): MResponse {
        return try {
            val res = datasourceSbc.retrieveSourceByCategory(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    //dataset management page (admin page)
    @RequirePermission
    @PostMapping("/DTSI009")
    fun retrieveDatasourceManagementPage(@RequestBody body: MData): MResponse {
        return try {
            val res = datasourceSbc.retrieveDatasourceManagementPage(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    // dataset upload summary (admin dashboard)
    @NotRequirePermission
    @PostMapping("/DTSI010")
    fun retrieveDatasourceHistorySummary(): MResponse =
        runCatching {
            CommonMessageCode.SUCCESS.generateIMResponse(datasourceSbc.retrieveDatasourceHistorySummaryForDashBoardPage())
        }.getOrElse { CommonMessageCode.GENERAL_ERROR.generateIMResponse(it as Exception) }
}
