package com.kh.niswebportal.app.approval

import com.kh.niswebportal.configuration.loader.AuthContext
import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.annotation.RequirePermission
import com.kh.niswebportal.core.builder.buildMData
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ApprovalCbc(
    private val approvalSbc: ApprovalSbc
) {

    /**
     * Retrieve list of approval requests with filtering & pagination
     * Params: status ('03' Pending, '01' Approved, '02' Rejected, '04' Cancelled), sourceId, search, page, size
     */
    @RequirePermission
    @PostMapping("/APPI001")
    fun retrieveApprovalList(@RequestBody(required = false) body: MData?): MResponse {
        return try {
            val res = approvalSbc.retrieveApprovalList(body ?: buildMData {})
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    /**
     * Retrieve detail information of a specific approval request
     * Params: uploadId
     */
    @RequirePermission
    @PostMapping("/APPI002")
    fun retrieveApprovalDetail(@RequestBody body: MData): MResponse {
        val uploadId = body.getString("uploadId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("uploadId is required"))
        return try {
            val res = approvalSbc.retrieveApprovalDetail(uploadId)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    /**
     * Preview staged data for an approval request
     * Params: sourceId, uploadId, page, size
     */
    @NotRequirePermission
    @PostMapping("/APPI003")
    fun previewApprovalData(@RequestBody body: MData): MResponse {
        val sourceId = body.getString("sourceId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("sourceId is required"))
        val uploadId = body.getString("uploadId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("uploadId is required"))

        val page = body.getInt("page") ?: 1
        val size = body.getInt("size") ?: 50

        return try {
            val res = approvalSbc.previewApprovalData(sourceId, uploadId, page, size)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    /**
     * Approve an upload request
     * Params: uploadId, comment (optional)
     */
    @RequirePermission
    @PostMapping("/APPR001")
    fun approveUpload(@RequestBody body: MData): MResponse {
        val uploadId = body.getString("uploadId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("uploadId is required"))
        val comment = body.getString("comment")

        return try {
            val res = approvalSbc.approveUpload(uploadId, AuthContext.userId(), comment)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    /**
     * Reject an upload request
     * Params: uploadId, reason (required)
     */
    @RequirePermission
    @PostMapping("/APPR002")
    fun rejectUpload(@RequestBody body: MData): MResponse {
        val uploadId = body.getString("uploadId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("uploadId is required"))
        val reason = body.getString("reason")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("reason is required for rejection"))

        return try {
            val res = approvalSbc.rejectUpload(uploadId, AuthContext.userId(), reason)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    /**
     * Cancel an upload request (by author)
     * Params: uploadId, reason (optional)
     */
    @RequirePermission
    @PostMapping("/APPR003")
    fun cancelUpload(@RequestBody body: MData): MResponse {
        val uploadId = body.getString("uploadId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("uploadId is required"))
        val reason = body.getString("reason")

        return try {
            val res = approvalSbc.cancelUpload(uploadId, AuthContext.userId(), reason)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    /**
     * Retrieve audit history trail for an upload approval request
     * Params: uploadId
     */
    @NotRequirePermission
    @PostMapping("/APPI004")
    fun retrieveApprovalAuditTrail(@RequestBody body: MData): MResponse {
        val uploadId = body.getString("uploadId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("uploadId is required"))
        return try {
            val res = approvalSbc.retrieveApprovalAuditTrail(uploadId)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }
}
