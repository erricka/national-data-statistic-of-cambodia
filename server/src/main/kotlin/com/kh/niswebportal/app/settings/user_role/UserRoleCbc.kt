package com.kh.niswebportal.app.settings.user_role

import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserRoleCbc(
    private val userRoleEbc: UserRoleEbc
) {

    @NotRequirePermission
    @PostMapping("/URRI001")
    fun retrieveRolesByUserID(@RequestBody body: MData): MResponse {

        return try {
            val roles = userRoleEbc.retrieveRolesByUserID(body)
            val res = com.kh.niswebportal.core.builder.buildMData { set("roles", roles) }
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/URRR001")
    fun assignRoleToUser(@RequestBody body: MData): MResponse {

        return try {
            val response = userRoleEbc.registerRoleToUser(body)
            val res = com.kh.niswebportal.core.builder.buildMData { set("assigned", response) }
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/URRR002")
    fun removeRoleFromUser(@RequestBody body: MData): MResponse {

        return try {
            val response = userRoleEbc.deleteRoleFromUser(body)
            if (response > 0) {
                val res = com.kh.niswebportal.core.builder.buildMData { set("removed", response) }
                CommonMessageCode.SUCCESS.generateIMResponse(res)
            } else {
                CommonMessageCode.GENERAL_ERROR.generateIMResponse(
                    messages = setOf("Remove failed")
                )
            }
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/URRI002")
    fun retrieveEffectivePermissionsByUsername(@RequestBody body: MData): MResponse {

        return try {
            val perms = userRoleEbc.retrievePermissionsByUserID(body)
            val res = com.kh.niswebportal.core.builder.buildMData { set("permissions", perms) }
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

}
