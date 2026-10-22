package com.kh.niswebportal.app.settings.user_role

import com.kh.niswebportal.core.com.MDao
import com.kh.niswebportal.core.com.MDaoSupport
import com.kh.niswebportal.core.com.MData
import org.springframework.stereotype.Repository

@Repository
class UserRoleEbc(mDao: MDao) : MDaoSupport(mDao) {

    fun retrieveRolesByUserID(param: MData): List<MData> {
        return selectList("retrieveRolesByUserID", param)
    }

    fun registerRoleToUser(param: MData): Int {
        return insert("registerRoleToUser", param)
    }

    fun deleteRoleFromUser(param: MData): Int {
        return delete("deleteRoleFromUser", param)
    }

    fun retrievePermissionsByUserID(param: MData): List<MData> {
        return selectList("retrievePermissionsByUserID", param)
    }
}
