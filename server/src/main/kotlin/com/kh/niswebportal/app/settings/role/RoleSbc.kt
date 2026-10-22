package com.kh.niswebportal.app.settings.role

import com.kh.niswebportal.app.settings.role_permission.RolePermissionEbc
import com.kh.niswebportal.core.com.MData
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Service
class RoleSbc(
    private val roleEbc: RoleEbc,
    private val rolePermissionEbc: RolePermissionEbc
) {
    fun retrieveListRole(request: MData): MData {
        val page = request.getInt("page") ?: 0
        val size = request.getInt("size") ?: 10
        val list = roleEbc.retrieveListRole(request)
        val total = roleEbc.countListRole(request)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun retrieveRoleById(body: MData): MData {
        val roleId = body.getString("roleId")
            ?: throw IllegalArgumentException("roleId is required")

        val rows: List<MData> = roleEbc.retrieveRolePermissionsById(body)

        if (rows.isEmpty()) {
            return com.kh.niswebportal.core.builder.buildMData {
                setString("roleId", roleId)
                set("permissions", emptyList<MData>())
            }
        }

        val firstRow = rows.first()
        val roleName = firstRow.getString("roleName") ?: ""

        val permissionsList = rows.mapNotNull { row ->
            val permId = row.getString("permissionId")
            if (permId != null) {
                com.kh.niswebportal.core.builder.buildMData {
                    setString("permissionId", permId)
                    setString("description", row.getString("description") ?: "")
                }
            } else null
        }

        return com.kh.niswebportal.core.builder.buildMData {
            setString("roleId", roleId)
            setString("roleName", roleName)
            set("permissions", permissionsList)
        }
    }

    @Transactional
    fun registerRole(body: MData): MData {
        val roleId = body.getString("roleId")?.trim()
            ?: throw IllegalArgumentException("roleId is required")

        // 1. Prepare audit timestamps and user session details
        val now = LocalDateTime.now()
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val timeStr = now.format(DateTimeFormatter.ofPattern("HHmmssSSS"))

        val currentUser = body.getString("firstRegisterUser") ?: "system"
        val currentBranch = body.getString("firstRegisterBranch") ?: ""

        body.setString("firstRegisterBranch", currentBranch)
        body.setString("firstRegisterUser", currentUser)
        body.setString("firstRegisterDate", dateStr)
        body.setString("firstRegisterTime", timeStr)
        body.setString("lastChangeBranch", currentBranch)
        body.setString("lastChangeUser", currentUser)
        body.setString("lastChangeDate", dateStr)
        body.setString("lastChangeTime", timeStr)

        // 2. Insert into public.roles
        val rows = roleEbc.registerRole(body)
        if (rows <= 0) throw RuntimeException("Failed to register base role")

        // 3. Extract permission list and insert into public.role_permissions
        val rawPermissions = body.get("permissionIds")
        val permissionList: List<String> = when (rawPermissions) {
            is List<*> -> rawPermissions.filterIsInstance<String>()
            is String -> rawPermissions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }

        permissionList.forEach { permId ->
            val permParam = com.kh.niswebportal.core.builder.buildMData {
                setString("roleId", roleId)
                setString("permissionId", permId)
                setString("status", body.getString("status") ?: "01")
                setString("firstRegisterBranch", currentBranch)
                setString("firstRegisterUser", currentUser)
                setString("firstRegisterDate", dateStr)
                setString("firstRegisterTime", timeStr)
                setString("lastChangeBranch", currentBranch)
                setString("lastChangeUser", currentUser)
                setString("lastChangeDate", dateStr)
                setString("lastChangeTime", timeStr)
            }
            rolePermissionEbc.registerPermissionToRole(permParam)
        }

        return com.kh.niswebportal.core.builder.buildMData {
            setString("roleId", roleId)
            set("assignedPermissionsCount", permissionList.size)
        }
    }

    @Transactional
    fun updateRole(body: MData): MData {
        val roleId = body.getString("roleId")?.trim()
            ?: throw IllegalArgumentException("roleId is required")

        // 1. Prepare audit timestamps and user session details
        val now = LocalDateTime.now()
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val timeStr = now.format(DateTimeFormatter.ofPattern("HHmmssSSS"))

        val currentUser = body.getString("lastChangeUser") ?: "system"
        val currentBranch = body.getString("lastChangeBranch") ?: ""

        body.setString("lastChangeBranch", currentBranch)
        body.setString("lastChangeUser", currentUser)
        body.setString("lastChangeDate", dateStr)
        body.setString("lastChangeTime", timeStr)

        // 2. Update base roles table
        val updatedRows = roleEbc.updateRole(body)

        // 3. Re-assign permissions if permissionIds field is provided in payload
        val rawPermissions = body.get("permissionIds")
        var assignedCount = 0

        if (rawPermissions != null) {
            val permissionList: List<String> = when (rawPermissions) {
                is List<*> -> rawPermissions.filterIsInstance<String>()
                is String -> rawPermissions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                else -> emptyList()
            }

            // Clear old mapping using existing DAO method
            rolePermissionEbc.deleteRolePermissions(body)

            // Re-insert permission list using registerPermissionToRole
            permissionList.forEach { permId ->
                val permParam = com.kh.niswebportal.core.builder.buildMData {
                    setString("roleId", roleId)
                    setString("permissionId", permId)
                    setString("status", body.getString("status") ?: "01")
                    setString("firstRegisterBranch", currentBranch)
                    setString("firstRegisterUser", currentUser)
                    setString("firstRegisterDate", dateStr)
                    setString("firstRegisterTime", timeStr)
                    setString("lastChangeBranch", currentBranch)
                    setString("lastChangeUser", currentUser)
                    setString("lastChangeDate", dateStr)
                    setString("lastChangeTime", timeStr)
                }
                rolePermissionEbc.registerPermissionToRole(permParam)
            }
            assignedCount = permissionList.size
        }

        return com.kh.niswebportal.core.builder.buildMData {
            set("updated", updatedRows)
            set("assignedPermissionsCount", assignedCount)
        }
    }

    fun deleteRole(body: MData): MData {
        val rows = roleEbc.deleteRole(body)
        return com.kh.niswebportal.core.builder.buildMData { set("deleted", rows) }
    }

    fun retrieveListRoleDropdown(request: MData): MData {
        val list = roleEbc.retrieveListRoleDropdown(request)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
        }
    }
}