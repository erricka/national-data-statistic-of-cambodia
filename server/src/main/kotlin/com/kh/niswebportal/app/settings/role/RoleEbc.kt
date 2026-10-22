package com.kh.niswebportal.app.settings.role

import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import org.springframework.stereotype.Repository

@Repository
class RoleEbc(mDao: MDao) : MDaoSupport(mDao) {
    fun retrieveListRole(request: MData): List<MData> =
        selectListForPage("retrieveListRole", request)

    fun countListRole(request: MData): Int =
        selectOne("countListRole", request).getInt("total") ?: 0

    fun retrieveRoleById(param: MData): MData =
        selectOne("retrieveRoleById", param)

    fun registerRole(param: MData): Int =
        insert("registerRole", param)

    fun updateRole(param: MData): Int =
        update("updateRole", param)

    fun deleteRole(param: MData): Int =
        update("deleteRole", param)

    fun retrieveListRoleDropdown(request: MData): List<MData> =
        selectListForPage("retrieveListRoleDropdown", request)

    fun retrieveRolePermissionsById(param: MData): List<MData> =
        selectList("retrieveRoleById", param)
}