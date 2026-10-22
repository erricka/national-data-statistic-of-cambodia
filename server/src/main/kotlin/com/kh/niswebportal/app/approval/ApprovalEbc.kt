package com.kh.niswebportal.app.approval

import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import com.kh.niswebportal.core.com.MData
import org.springframework.stereotype.Repository

@Repository
class ApprovalEbc(mDao: MDao) : MDaoSupport(mDao) {

    fun retrieveApprovalList(param: MData): List<MData> =
        selectListForPage("retrieveApprovalList", param)

    fun countApprovalList(param: MData): Int =
        selectOne("countApprovalList", param).getInt("total") ?: 0

    fun retrieveApprovalDetail(param: MData): MData? =
        runCatching { selectOne("retrieveApprovalDetail", param) }.getOrNull()

    fun updateDatasourceHistoryStatus(param: MData): Int =
        update("updateDatasourceHistoryStatus", param)

    fun supersedePreviousApprovedHistory(param: MData): Int =
        update("supersedePreviousApprovedHistory", param)

    fun registerApprovalAudit(param: MData): Int =
        insert("registerApprovalAudit", param)

    fun retrieveApprovalAuditTrail(param: MData): List<MData> =
        selectList("retrieveApprovalAuditTrail", param)
}
