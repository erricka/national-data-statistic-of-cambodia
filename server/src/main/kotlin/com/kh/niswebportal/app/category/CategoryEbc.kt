package com.kh.niswebportal.app.category

import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import com.kh.niswebportal.core.com.MData
import org.springframework.stereotype.Repository

@Repository
class CategoryEbc(mDao: MDao) : MDaoSupport(mDao) {
    fun retrieveListCategory(request: MData): List<MData> =
        selectListForPage("retrieveListCategory", request)

    fun countListCategory(request: MData): Int =
        selectOne("countListCategory", request).getInt("total")

    fun registerCategory(param: MData): Int =
        insert("registerCategory", param)

    fun updateCategory(param: MData): Int =
        update("updateCategory", param)

    fun retrieveListCategoryHome(): List<MData> =
        selectListForPage("retrieveListCategoryHome",  MData())

    fun retrieveListCategoryPage(request: MData): List<MData> =
        selectListForPage("retrieveListCategoryPage", request)

    fun countListCategoryPage(request: MData): Int =
        selectOne("countListCategoryPage", request).getInt("total")

    fun retrieveCategorySummary(): MData =
        selectOne("retrieveCategorySummary", MData())

    fun retrieveCategoryDropdown(request: MData): List<MData> =
        selectListForPage("retrieveCategoryDropdown", request)
}