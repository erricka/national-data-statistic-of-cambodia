package com.kh.niswebportal.app.settings.role_permission

import com.kh.niswebportal.core.com.MData
import org.springframework.stereotype.Service
import kotlin.math.ceil

@Service
class RolePermissionSbc(
    private val rolePermissionEbc: RolePermissionEbc
) {
    fun retrieveAllMenus(body: MData): MData {
        val page = body.getInt("page") ?: 0
        val size = body.getInt("size") ?: 10
        val list = rolePermissionEbc.retrieveAllMenus(body)
        val total = rolePermissionEbc.countAllMenus(body)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun retrieveAllPermissionsByMenu(body: MData): MData {
        val page = body.getInt("page") ?: 0
        val size = body.getInt("size") ?: 10
        val list = rolePermissionEbc.retrieveAllPermissionsByMenu(body)
        val total = rolePermissionEbc.countAllPermissionsByMenu(body)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun retrieveAssignedMenusByRole(body: MData): MData {
        val page = body.getInt("page") ?: 0
        val size = body.getInt("size") ?: 10
        val list = rolePermissionEbc.retrieveAssignedMenusByRole(body)
        val total = rolePermissionEbc.countAssignedMenusByRole(body)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun retrieveAssignedPermissionsByRoleAndMenu(body: MData): MData {
        val page = body.getInt("page") ?: 0
        val size = body.getInt("size") ?: 10
        val list = rolePermissionEbc.retrieveAssignedPermissionsByRoleAndMenu(body)
        val total = rolePermissionEbc.countAssignedPermissionsByRoleAndMenu(body)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun assignPermissionsToRole(body: MData): MData {
        @Suppress("UNCHECKED_CAST")
        val permissionIds = body.get("permissionIds") as List<String>
        val roleId = body.getString("roleId")!!
        var inserted = 0
        var skipped = 0
        permissionIds.forEach { permissionId ->
            val param = com.kh.niswebportal.core.builder.buildMData {
                setString("roleId", roleId)
                setString("permissionId", permissionId)
            }
            val rows = rolePermissionEbc.registerPermissionToRole(param)
            if (rows > 0) inserted++ else skipped++
        }
        return com.kh.niswebportal.core.builder.buildMData {
            set("inserted", inserted)
            set("skipped", skipped)
            set("total", permissionIds.size)
        }
    }

    fun removePermissionFromRole(body: MData): MData {
        val rows = rolePermissionEbc.removePermissionFromRole(body)
        if (rows <= 0) throw RuntimeException("Remove failed")
        return com.kh.niswebportal.core.builder.buildMData { set("removed", rows) }
    }
}