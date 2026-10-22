package com.kh.niswebportal.core.com

interface MDao {
    fun insert(statementID: String, parameter: MData): Int
    fun selectOne(statementID: String, parameter: MData): MData
    fun selectOneForUpdate(statementID: String, parameter: MData): MData

    fun selectList(statementID: String): List<MData>
    fun selectList(statementID: String, parameter: MData): List<MData>
    fun selectListForPage(statementID: String, parameter: MData): List<MData>

    fun update(statementID: String, parameter: MData): Int
    fun delete(statementID: String, parameter: MData): Int

    fun selectListLong(statementID: String, userId: Long): List<Long>
    fun selectListString(statementID: String, perId: Long): String?
}
