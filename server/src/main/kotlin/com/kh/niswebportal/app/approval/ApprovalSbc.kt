package com.kh.niswebportal.app.approval

import com.kh.niswebportal.app.datasource.DatasourceEbc
import com.kh.niswebportal.configuration.loader.AuthContext
import com.kh.niswebportal.core.builder.buildMData
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Service
class ApprovalSbc(
    private val approvalEbc: ApprovalEbc,
    private val datasourceEbc: DatasourceEbc,
    inquiryStrategies: List<NisInquiryStrategy>
) {
    private val inquiryMap = inquiryStrategies.associateBy { it.sourceId.lowercase() }

    companion object {
        const val STATUS_APPROVED = "01"
        const val STATUS_REJECTED = "02"
        const val STATUS_PENDING = "03"
        const val STATUS_CANCELLED = "04"

        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HHmmssSSS")
    }

    fun retrieveApprovalList(request: MData): MData {
        val page = request.getInt("page") ?: 1
        val size = request.getInt("size") ?: 10

        // Default to PENDING_APPROVAL ('03') if not explicitly specified
        if (request.getString("status").isNullOrBlank()) {
            request.setString("status", STATUS_PENDING)
        }

        val list = approvalEbc.retrieveApprovalList(request)
        val total = approvalEbc.countApprovalList(request)

        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", if (size > 0) ceil(total.toDouble() / size).toInt() else 1)
        }
    }

    fun retrieveApprovalDetail(uploadId: String): MData {
        val param = buildMData { setString("uploadId", uploadId) }
        return approvalEbc.retrieveApprovalDetail(param)
            ?: throw IllegalArgumentException("Approval request not found for upload ID: $uploadId")
    }

    fun previewApprovalData(sourceId: String, uploadId: String, page: Int, size: Int): MData {
        val strategy = inquiryMap[sourceId.lowercase()]
            ?: throw IllegalArgumentException("No inquiry strategy found for source ID: $sourceId")

        val cleanedUploadId = uploadId.replace(Regex("""\.(xlsx|csv)$"""), "")
        val res = strategy.getDynamicReport(cleanedUploadId)

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
            set("title", res.title)
            set("subTitle", res.sub_title)
            set("header", headers)
            set("list", pagedRows)
            set("total", total)
            set("totalPages", if (validSize > 0) ceil(total.toDouble() / validSize).toInt() else 1)
            set("page", validPage)
            set("size", validSize)
        }
    }

    @Transactional
    fun approveUpload(uploadId: String, approverUser: String?, comment: String?): MData {
        val currentActor = approverUser ?: AuthContext.userId()
        val detail = retrieveApprovalDetail(uploadId)
        val currentStatus = detail.getString("status") ?: STATUS_PENDING

        if (currentStatus != STATUS_PENDING) {
            throw IllegalStateException("Only pending approval requests ('$STATUS_PENDING') can be approved. Current status: $currentStatus")
        }

        val submittedBy = detail.getString("submittedBy") ?: ""
        if(submittedBy != "ADMIN"){
            // Maker-Checker validation (Four-Eyes Principle)
            if (submittedBy.isNotBlank() && submittedBy.equals(currentActor, ignoreCase = true) && currentActor != "system") {
                throw IllegalArgumentException("Maker-Checker violation: Submitter ($submittedBy) cannot approve their own upload.")
            }
        }

        val sourceId = detail.getString("sourceId") ?: ""

        // 1. Supersede previous active uploads for this sourceId
        val supersedeParam = buildMData {
            setString("sourceId", sourceId)
            setString("uploadId", uploadId)
            setString("lastChangeUser", currentActor)
            setString("lastChangeDate", LocalDate.now().format(DATE_FMT))
            setString("lastChangeTime", LocalTime.now().format(TIME_FMT))
        }
        approvalEbc.supersedePreviousApprovedHistory(supersedeParam)

        // 2. Update datasource_history status to APPROVED ('01')
        val updateParam = buildMData {
            setString("uploadId", uploadId)
            setString("status", STATUS_APPROVED)
            setString("lastChangeUser", currentActor)
            setString("lastChangeDate", LocalDate.now().format(DATE_FMT))
            setString("lastChangeTime", LocalTime.now().format(TIME_FMT))
        }
        approvalEbc.updateDatasourceHistoryStatus(updateParam)

        // 3. Record in upload_approval_audit
        val auditParam = buildMData {
            setString("uploadId", uploadId)
            setString("sourceId", sourceId)
            setString("action", "APPROVED")
            setString("actionBy", currentActor)
            setString("firstRegisterDate", LocalDate.now().format(DATE_FMT))
            setString("firstRegisterTime", LocalTime.now().format(TIME_FMT))
            setString("previousStatus", currentStatus)
            setString("newStatus", STATUS_APPROVED)
            setString("reason", comment ?: "Approved by $currentActor")
        }
        approvalEbc.registerApprovalAudit(auditParam)

        return buildMData {
            setBoolean("success", true)
            setString("uploadId", uploadId)
            setString("status", STATUS_APPROVED)
            setString("message", "Upload approved successfully. Data is now active in production.")
        }
    }

    @Transactional
    fun rejectUpload(uploadId: String, approverUser: String?, reason: String): MData {
        if (reason.isBlank()) {
            throw IllegalArgumentException("Rejection reason is required.")
        }

        val currentActor = approverUser ?: AuthContext.userId()
        val detail = retrieveApprovalDetail(uploadId)
        val currentStatus = detail.getString("status") ?: STATUS_PENDING

        if (currentStatus != STATUS_PENDING) {
            throw IllegalStateException("Only pending approval requests ('$STATUS_PENDING') can be rejected. Current status: $currentStatus")
        }

        val sourceId = detail.getString("sourceId") ?: ""

        // 1. Update datasource_history status to REJECTED ('02')
        val updateParam = buildMData {
            setString("uploadId", uploadId)
            setString("status", STATUS_REJECTED)
            setString("lastChangeUser", currentActor)
            setString("lastChangeDate", LocalDate.now().format(DATE_FMT))
            setString("lastChangeTime", LocalTime.now().format(TIME_FMT))
        }
        approvalEbc.updateDatasourceHistoryStatus(updateParam)

        // 2. Record in upload_approval_audit
        val auditParam = buildMData {
            setString("uploadId", uploadId)
            setString("sourceId", sourceId)
            setString("action", "REJECTED")
            setString("actionBy", currentActor)
            setString("firstRegisterDate", LocalDate.now().format(DATE_FMT))
            setString("firstRegisterTime", LocalTime.now().format(TIME_FMT))
            setString("previousStatus", currentStatus)
            setString("newStatus", STATUS_REJECTED)
            setString("reason", reason)
        }
        approvalEbc.registerApprovalAudit(auditParam)

        return buildMData {
            setBoolean("success", true)
            setString("uploadId", uploadId)
            setString("status", STATUS_REJECTED)
            setString("message", "Upload rejected successfully.")
        }
    }

    @Transactional
    fun cancelUpload(uploadId: String, currentUser: String?, reason: String?): MData {
        val currentActor = currentUser ?: AuthContext.userId()
        val detail = retrieveApprovalDetail(uploadId)
        val currentStatus = detail.getString("status") ?: STATUS_PENDING

        if (currentStatus != STATUS_PENDING) {
            throw IllegalStateException("Only pending approval requests ('$STATUS_PENDING') can be cancelled. Current status: $currentStatus")
        }

        val submittedBy = detail.getString("submittedBy") ?: ""
        if(submittedBy != "ADMIN"){
            if (submittedBy.isNotBlank() && !submittedBy.equals(currentActor, ignoreCase = true) && currentActor != "system") {
                throw IllegalArgumentException("You can only cancel your own uploads.")
            }
        }

        val sourceId = detail.getString("sourceId") ?: ""

        // 1. Update datasource_history status to CANCELLED ('04')
        val updateParam = buildMData {
            setString("uploadId", uploadId)
            setString("status", STATUS_CANCELLED)
            setString("lastChangeUser", currentActor)
            setString("lastChangeDate", LocalDate.now().format(DATE_FMT))
            setString("lastChangeTime", LocalTime.now().format(TIME_FMT))
        }
        approvalEbc.updateDatasourceHistoryStatus(updateParam)

        // 2. Record in upload_approval_audit
        val auditParam = buildMData {
            setString("uploadId", uploadId)
            setString("sourceId", sourceId)
            setString("action", "CANCELLED")
            setString("actionBy", currentActor)
            setString("firstRegisterDate", LocalDate.now().format(DATE_FMT))
            setString("firstRegisterTime", LocalTime.now().format(TIME_FMT))
            setString("previousStatus", currentStatus)
            setString("newStatus", STATUS_CANCELLED)
            setString("reason", reason ?: "Cancelled by author")
        }
        approvalEbc.registerApprovalAudit(auditParam)

        return buildMData {
            setBoolean("success", true)
            setString("uploadId", uploadId)
            setString("status", STATUS_CANCELLED)
            setString("message", "Upload cancelled successfully.")
        }
    }

    fun retrieveApprovalAuditTrail(uploadId: String): MData {
        val param = buildMData { setString("uploadId", uploadId) }
        val list = approvalEbc.retrieveApprovalAuditTrail(param)
        return buildMData {
            set("list", list)
            set("total", list.size)
        }
    }
}
