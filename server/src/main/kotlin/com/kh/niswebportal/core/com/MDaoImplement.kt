package com.kh.niswebportal.core.com

import org.mybatis.spring.SqlSessionTemplate
import org.springframework.context.annotation.Primary
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Primary
@Component
class MDaoImplement(
    private val sqlSessionTemplate: SqlSessionTemplate
) : MDao {

    override fun insert(statementID: String, parameter: MData): Int {
        applyInsertAudit(statementID, parameter)
        return sqlSessionTemplate.insert(statementID, parameter)
    }

    override fun update(statementID: String, parameter: MData): Int {
        applyUpdateAudit(statementID, parameter)
        return sqlSessionTemplate.update(statementID, parameter)
    }

    override fun delete(statementID: String, parameter: MData): Int {
        applyUpdateAudit(statementID, parameter)
        return sqlSessionTemplate.delete(statementID, parameter)
    }

    override fun selectOne(statementID: String, parameter: MData): MData =
        sqlSessionTemplate.selectOne(statementID, parameter)

    override fun selectOneForUpdate(statementID: String, parameter: MData): MData =
        sqlSessionTemplate.selectOne(statementID, parameter)

    override fun selectList(statementID: String): List<MData> =
        sqlSessionTemplate.selectList(statementID)

    override fun selectList(statementID: String, parameter: MData): List<MData> =
        sqlSessionTemplate.selectList(statementID, parameter)

//    override fun selectListForPage(statementID: String, parameter: MData): List<MData> {
//        val page = parameter.getInt("page") ?: 0
//        val size = parameter.getInt("size") ?: 10
//        parameter.set("offset", page * size)
//        parameter.set("limit", size)
//        return sqlSessionTemplate.selectList(statementID, parameter)
//    }

    override fun selectListForPage(statementID: String, parameter: MData): List<MData> {
        val page = parameter.getInt("page") ?: 1
        val size = parameter.getInt("size") ?: 10

        val safePage = if (page < 1) 1 else page
        val offset = (safePage - 1) * size

        parameter.set("offset", offset)
        parameter.set("limit", size)
        return sqlSessionTemplate.selectList(statementID, parameter)
    }

    override fun selectListLong(statementID: String, userId: Long): List<Long> =
        sqlSessionTemplate.selectList(statementID, userId)

    override fun selectListString(statementID: String, perId: Long): String? =
        sqlSessionTemplate.selectOne(statementID, perId)

    private fun applyInsertAudit(statementID: String, p: MData) {
        if (!shouldApplyAudit(statementID)) return

        val currentUser = currentUsername()
        val currentDate = nowDate()
        val currentTime = nowTime()

        if (p.getString("firstRegisterUser").isNullOrBlank()) p.setString("firstRegisterUser", currentUser)
        if (p.getString("firstRegisterDate").isNullOrBlank()) p.setString("firstRegisterDate", currentDate)
        if (p.getString("firstRegisterTime").isNullOrBlank()) p.setString("firstRegisterTime", currentTime)

        p.setString("lastChangeUser", currentUser)
        p.setString("lastChangeDate", currentDate)
        p.setString("lastChangeTime", currentTime)
    }

    private fun applyUpdateAudit(statementID: String, p: MData) {
        if (!shouldApplyAudit(statementID)) return

        val currentUser = currentUsername()
        val currentDate = nowDate()
        val currentTime = nowTime()

        p.setString("lastChangeUser", currentUser)
        p.setString("lastChangeDate", currentDate)
        p.setString("lastChangeTime", currentTime)

        if (statementID.contains("delete", ignoreCase = true)) {
            if (p.getString("deletedBy").isNullOrBlank()) p.setString("deletedBy", currentUser)
            if (p.getString("deletedDate").isNullOrBlank()) p.setString("deletedDate", currentDate)
            if (p.getString("deletedTime").isNullOrBlank()) p.setString("deletedTime", currentTime)
        }
    }

    private fun shouldApplyAudit(statementID: String): Boolean {
        val id = statementID.lowercase()
        if (id.contains("auditlog")) return false
        return id.contains("register") || id.contains("insert") || id.contains("update") || id.contains("delete")
    }

    private fun currentUsername(): String =
        SecurityContextHolder.getContext().authentication?.name ?: "system"

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val TIME_FMT = DateTimeFormatter.ofPattern("HHmmssSSS")

    private fun nowDate(): String =
        LocalDate.now().format(DATE_FMT)

    private fun nowTime(): String =
        LocalTime.now().format(TIME_FMT)

}
