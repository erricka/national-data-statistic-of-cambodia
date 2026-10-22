package com.kh.niswebportal.core.com

open class MDaoSupport(
    protected val mDao: MDao
) {
    protected fun insert(statementID: String, parameter: MData): Int =
        mDao.insert(statementID, parameter)

    protected fun selectOne(statementID: String, parameter: MData): MData =
        mDao.selectOne(statementID, parameter)

    protected fun selectOneForUpdate(statementID: String, parameter: MData): MData =
        mDao.selectOneForUpdate(statementID, parameter)

    protected fun selectList(statementID: String): List<MData> =
        mDao.selectList(statementID)

    protected fun selectList(statementID: String, parameter: MData): List<MData> =
        mDao.selectList(statementID, parameter)

    protected fun selectListForPage(statementID: String, parameter: MData): List<MData> =
        mDao.selectListForPage(statementID, parameter)

    protected fun update(statementID: String, parameter: MData): Int =
        mDao.update(statementID, parameter)

    protected fun delete(statementID: String, parameter: MData): Int =
        mDao.delete(statementID, parameter)

    protected fun selectListLong(statementID: String, userId: Long): List<Long> =
        mDao.selectListLong(statementID, userId)

    protected fun selectListString(statementID: String, perId: Long): String? =
        mDao.selectListString(statementID, perId)
}
