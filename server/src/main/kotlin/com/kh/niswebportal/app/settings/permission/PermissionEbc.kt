package com.kh.niswebportal.app.settings.permission

import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import org.springframework.stereotype.Repository
@Repository
class PermissionEbc(mDao: MDao) : MDaoSupport(mDao) {
    fun retrieveListPermission(request: MData): List<MData> =
        selectListForPage("retrieveListPermission", request)

    fun countListPermission(request: MData): Int =
        selectOne("countListPermission", request).getInt("total") ?: 0

    fun retrieveListMenu(request: MData): List<MData> =
        selectListForPage("retrieveListMenu", request)

    fun countListMenu(request: MData): Int =
        selectOne("countListMenu", request).getInt("total") ?: 0

    fun retrievePermissionById(param: MData): MData =
        selectOne("retrievePermissionById", param)

    fun registerPermission(param: MData): Int =
        insert("registerPermission", param)

    fun updatePermission(param: MData): Int =
        update("updatePermission", param)

    fun deletePermission(param: MData): Int =
        update("deletePermission", param)

    fun retrieveListAllPermissionForRegisterRole(request: MData): List<MData> =
        selectListForPage("retrieveListAllPermissionForRegisterRole", request)
}