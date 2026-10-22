package com.kh.niswebportal.app.settings.permission

import com.kh.niswebportal.core.com.MData
import org.springframework.stereotype.Service
import kotlin.math.ceil

@Service
class PermissionSbc(
    private val permissionEbc: PermissionEbc
) {
    fun retrieveListPermission(request: MData): MData {
        val page = request.getInt("page") ?: 0
        val size = request.getInt("size") ?: 10
        val list = permissionEbc.retrieveListPermission(request)
        val total = permissionEbc.countListPermission(request)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun retrieveListMenu(request: MData): MData {
        val page = request.getInt("page") ?: 0
        val size = request.getInt("size") ?: 10
        val list = permissionEbc.retrieveListMenu(request)
        val total = permissionEbc.countListMenu(request)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun retrievePermissionById(body: MData): MData =
        permissionEbc.retrievePermissionById(body)

    fun registerPermission(body: MData): MData {
        val rows = permissionEbc.registerPermission(body)
        if (rows <= 0) throw RuntimeException("Insert failed")
        return com.kh.niswebportal.core.builder.buildMData {
            setString(
                "permissionId",
                body.getString("permissionId")
            )
        }
    }

    fun updatePermission(body: MData): MData {
        val rows = permissionEbc.updatePermission(body)
        return com.kh.niswebportal.core.builder.buildMData { set("updated", rows) }
    }

    fun deletePermission(body: MData): MData {
        val rows = permissionEbc.deletePermission(body)
        return com.kh.niswebportal.core.builder.buildMData { set("deleted", rows) }
    }

    fun retrieveListAllPermissionForRegisterRole(request: MData): MData {
        val list = permissionEbc.retrieveListAllPermissionForRegisterRole(request)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
        }
    }
}