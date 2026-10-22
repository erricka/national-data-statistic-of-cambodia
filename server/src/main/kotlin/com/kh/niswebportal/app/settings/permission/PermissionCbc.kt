package com.kh.niswebportal.app.settings.permission

import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.annotation.RequirePermission
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PermissionCbc(
    private val permissionSbc: com.kh.niswebportal.app.settings.permission.PermissionSbc
) {

    //admin page
    @RequirePermission
    @PostMapping("/PERI001")
    fun retrieveListPermission(@RequestBody body: MData): MResponse {
        return try {
            val res = permissionSbc.retrieveListPermission(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/PERI002")
    fun retrievePermissionById(@RequestBody body: MData): MResponse {
        val permissionId = body.getString("permissionId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("permissionId is required")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("permissionId", permissionId)
        }
        return try {
            val permission = permissionSbc.retrievePermissionById(param)
            CommonMessageCode.SUCCESS.generateIMResponse(permission)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/PERI003")
    fun retrieveListMenu(@RequestBody body: MData): MResponse {
        return try {
            val res = permissionSbc.retrieveListMenu(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/PERR001")
    fun registerPermission(@RequestBody body: MData): MResponse {
        val permissionId = body.getString("permissionId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("permissionId is required")
            )
        val deleted = body.getString("deleted")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("deleted is required (Y/N)")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("permissionId", permissionId)
            setString("deleted", deleted)
        }
        return try {
            val res = permissionSbc.registerPermission(param)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/PERU001")
    fun updatePermission(@RequestBody body: MData): MResponse {
        val permissionId = body.getString("permissionId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("permissionId is required")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("permissionId", permissionId)
        }
        return try {
            val res = permissionSbc.updatePermission(param)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/PERD001")
    fun deletePermission(@RequestBody body: MData): MResponse {
        val permissionId = body.getString("permissionId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("permissionId is required")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("permissionId", permissionId)
        }
        return try {
            val res = permissionSbc.deletePermission(param)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/PERI004")
    fun retrieveListAllPermissionForRegisterRole(@RequestBody body: MData): MResponse {
        return try {
            val res = permissionSbc.retrieveListAllPermissionForRegisterRole(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }
}