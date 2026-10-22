package com.kh.niswebportal.app.settings.role

import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.annotation.RequirePermission
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RoleCbc(
    private val roleSbc: RoleSbc
) {

    @RequirePermission
    @PostMapping("/ROLI001")
    fun retrieveListRole(@RequestBody body: MData): MResponse {
        return try {
            val res = roleSbc.retrieveListRole(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/ROLI002")
    fun retrieveRoleById(@RequestBody body: MData): MResponse {
        val roleId = body.getString("roleId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("roleId is required")
            )

        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("roleId", roleId)
        }

        return try {
            val roleData = roleSbc.retrieveRoleById(param)
            CommonMessageCode.SUCCESS.generateIMResponse(roleData)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/ROLI003")
    fun retrieveListRoleDropdown(@RequestBody body: MData): MResponse {
        //used in dropdown select row page register user
        return try {
            val res = roleSbc.retrieveListRoleDropdown(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/ROLR001")
    fun registerRole(@RequestBody body: MData): MResponse {
        val roleId = body.getString("roleId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("roleId is required")
            )
        val name = body.getString("name")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("name is required")
            )

        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("roleId", roleId)
            setString("name", name)
            body.getString("description")?.trim()?.let { setString("description", it) }
            body.getString("status")?.trim()?.let { setString("status", it) }
        }

        return try {
            val res = roleSbc.registerRole(param)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/ROLU001")
    fun updateRole(@RequestBody body: MData): MResponse {
        val roleId = body.getString("roleId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("roleId is required")
            )

        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("roleId", roleId)
            body.getString("name")?.trim()?.let { setString("name", it) }
            body.getString("description")?.trim()?.let { setString("description", it) }
            body.getString("status")?.trim()?.let { setString("status", it) }

            // Pass raw permissionIds array directly through to SBC
            body.get("permissionIds")?.let { set("permissionIds", it) }
        }

        return try {
            val res = roleSbc.updateRole(param)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/ROLD001")
    fun deleteRole(@RequestBody body: MData): MResponse {
        val roleId = body.getString("roleId")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("roleId is required")
            )
        val param = com.kh.niswebportal.core.builder.buildMData(appendMData = body) {
            setString("roleId", roleId)
        }
        return try {
            val res = roleSbc.deleteRole(param)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }
}