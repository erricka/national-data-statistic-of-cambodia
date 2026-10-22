package com.kh.niswebportal.app.audit

import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import org.springframework.stereotype.Repository

@Repository
class AuditLogEbc(mDao: MDao) : MDaoSupport(mDao) {
        fun registerAuditLog(param: MData): Int {
        return insert("registerAuditLog", param)
    }
}