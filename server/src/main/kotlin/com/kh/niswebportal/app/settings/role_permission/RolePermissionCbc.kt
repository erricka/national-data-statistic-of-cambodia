package com.kh.niswebportal.app.settings.role_permission

import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RolePermissionCbc(
    private val rolePermissionSbc: RolePermissionSbc
) {

    @NotRequirePermission
    @PostMapping("/RLPI001")
    fun retrieveAllMenus(@RequestBody body: MData): MResponse {
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(rolePermissionSbc.retrieveAllMenus(body))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/RLPI002")
    fun retrieveAllPermissionsByMenu(@RequestBody body: MData): MResponse {
        val menuId = body.getString("menuId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("menuId is required")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("menuId", menuId)
            body.getString("permissionId")?.trim()?.takeIf { it.isNotEmpty() }?.let { setString("permissionId", it) }
        }
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(rolePermissionSbc.retrieveAllPermissionsByMenu(param))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @PostMapping("/RLPI003")
    fun retrieveAssignedMenusByRole(@RequestBody body: MData): MResponse {
        val roleId = body.getString("roleId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("roleId is required")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) { setString("roleId", roleId) }
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(rolePermissionSbc.retrieveAssignedMenusByRole(param))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @PostMapping("/RLPI004")
    fun retrieveAssignedPermissionsByRoleAndMenu(@RequestBody body: MData): MResponse {
        val roleId = body.getString("roleId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("roleId is required")
            )
        val menuId = body.getString("menuId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("menuId is required")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("roleId", roleId)
            setString("menuId", menuId)
            body.getString("permissionId")?.trim()?.takeIf { it.isNotEmpty() }?.let { setString("permissionId", it) }
        }
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(rolePermissionSbc.retrieveAssignedPermissionsByRoleAndMenu(param))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @PostMapping("/RLPR001")
    fun assignPermissionsToRole(@RequestBody body: MData): MResponse {
        val roleId = body.getString("roleId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("roleId is required")
            )
        @Suppress("UNCHECKED_CAST")
        val permissionIds = (body.get("permissionIds") as? List<String>)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("permissionIds is required and must be a non-empty list")
            )
        if (permissionIds.isEmpty()) {
            return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("permissionIds must contain at least one entry")
            )
        }
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("roleId", roleId)
            set("permissionIds", permissionIds)
        }
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(rolePermissionSbc.assignPermissionsToRole(param))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @PostMapping("/RLPD001")
    fun removePermissionFromRole(@RequestBody body: MData): MResponse {
        val roleId = body.getString("roleId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("roleId is required")
            )
        val permissionId = body.getString("permissionId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("permissionId is required")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("roleId", roleId)
            setString("permissionId", permissionId)
        }
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(rolePermissionSbc.removePermissionFromRole(param))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }
}