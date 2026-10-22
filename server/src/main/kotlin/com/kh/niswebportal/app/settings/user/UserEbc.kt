package com.kh.niswebportal.app.settings.user

import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import com.kh.niswebportal.core.com.MData
import org.springframework.stereotype.Repository

@Repository
class UserEbc(mDao: MDao) : MDaoSupport(mDao) {

    fun retrieveListUser(request: MData): List<MData> =
        selectListForPage("retrieveListUser", request)

    fun countListUser(request: MData): Int =
        selectOne("countListUser", request).getInt("total") ?: 0

    fun retrieveUserByUserID(param: MData): MData =
        selectOne("retrieveUserByUserID", param)

    fun retrieveRolesByUserID(param: MData): List<MData> =
        selectList("retrieveRolesByUserID", param)

    fun registerUser(param: MData): Int =
        insert("registerUser", param)

    fun updateUser(param: MData): Int =
        update("updateUser", param)

    fun deleteUser(param: MData): Int =
        update("deleteUser", param)

    fun deleteUserRoles(param: MData): Int =
        delete("deleteUserRoles", param)

    fun updateUserPassword(body: MData): Int =
        update("updateUserPassword", body)

    fun registerUserRole(param: MData): Int =
        insert("registerUserRole", param)

    fun changeUserPassword(param: MData): Int =
        update("changeUserPassword", param)
}