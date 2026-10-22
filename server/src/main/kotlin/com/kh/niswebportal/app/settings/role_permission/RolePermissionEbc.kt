package com.kh.niswebportal.app.settings.role_permission

import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import org.springframework.stereotype.Repository

@Repository
class RolePermissionEbc(mDao: MDao) : MDaoSupport(mDao) {

    fun retrieveAllMenus(param: MData): List<MData> =
        selectListForPage("retrieveAllMenus", param)

    fun countAllMenus(param: MData): Int =
        selectOne("countAllMenus", param).getInt("total") ?: 0

    fun retrieveAllPermissionsByMenu(param: MData): List<MData> =
        selectListForPage("retrieveAllPermissionsByMenu", param)

    fun countAllPermissionsByMenu(param: MData): Int =
        selectOne("countAllPermissionsByMenu", param).getInt("total") ?: 0

    fun retrieveAssignedMenusByRole(param: MData): List<MData> =
        selectListForPage("retrieveAssignedMenusByRole", param)

    fun countAssignedMenusByRole(param: MData): Int =
        selectOne("countAssignedMenusByRole", param).getInt("total") ?: 0

    fun retrieveAssignedPermissionsByRoleAndMenu(param: MData): List<MData> =
        selectListForPage("retrieveAssignedPermissionsByRoleAndMenu", param)

    fun countAssignedPermissionsByRoleAndMenu(param: MData): Int =
        selectOne("countAssignedPermissionsByRoleAndMenu", param).getInt("total") ?: 0

    fun registerPermissionToRole(param: MData): Int =
        insert("registerPermissionToRole", param)

    fun removePermissionFromRole(param: MData): Int =
        delete("removePermissionFromRole", param)

    fun deleteRolePermissions(param: MData): Int =
        delete("deleteRolePermissions", param)
}