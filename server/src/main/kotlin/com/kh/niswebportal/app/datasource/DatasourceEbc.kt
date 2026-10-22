package com.kh.niswebportal.app.datasource

import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import org.springframework.stereotype.Repository

@Repository
class DatasourceEbc(mDao: MDao) : MDaoSupport(mDao) {

    fun listSources(param: MData): List<MData> =
        selectListForPage("listSources", param)

    fun countSources(param: MData): Int =
        selectOne("countSources", param).getInt("total") ?: 0

    fun retrieveSource(param: MData): MData? =
        runCatching { selectOne("retrieveSource", param) }.getOrNull()

    fun previewSourceData(param: MData): List<MData> =
        selectListForPage("previewSourceData", param)

    fun countSourceData(param: MData): Int =
        selectOne("countSourceData", param).getInt("total") ?: 0

    fun registerSource(param: MData): Int =
        insert("registerSource", param)

    fun updateSource(param: MData): Int =
        update("updateSource", param)

    fun deleteSource(param: MData): Int =
        delete("deleteSource", param)

    fun registerUploadLog(param: MData): Int =
        insert("registerUploadLog", param)

    fun completeUploadLog(param: MData): Int =
        update("completeUploadLog", param)

    fun updateUploadLogTotalRows(param: MData): Int =
        update("updateUploadLogTotalRows", param)

    fun listUploadHistory(param: MData): List<MData> =
        selectList("listUploadHistory", param)

    fun registerDatasourceHistory(param: MData): Int =
        insert("registerDatasourceHistory", param)

    fun retrieveRecentSource(param: MData): List<MData> =
        selectListForPage("retrieveRecentSource", param)

    fun retrieveSourceByName(param: MData): List<MData> =
        selectListForPage("retrieveSourceByName", param)

    fun retrieveSourceByCategory(param: MData): List<MData> =
        selectListForPage("retrieveSourceByCategory", param)


    fun retrieveSourceById(param: MData): MData =
        selectOne("retrieveSourceById", param)

    fun retrieveDatasourceByCategory(request: MData): List<MData> =
        selectListForPage("retrieveDatasourceByCategory", request)

    fun countDatasourceByCategory(request: MData): Int =
        selectOne("countDatasourceByCategory", request).getInt("total")

    fun retrieveDatasourceManagementPage(request: MData): List<MData> =
        selectListForPage("retrieveDatasourceManagementPage", request)

    fun countDatasourceManagementPage(request: MData): Int =
        selectOne("countDatasourceManagementPage", request).getInt("total")

    fun retrieveDatasourceHistorySummaryForDashBoardPage(param: MData): MData =
        selectOne("retrieveDatasourceHistorySummaryForDashBoardPage", param)
}