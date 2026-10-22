package com.kh.niswebportal.app.category

import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.annotation.RequirePermission
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CategoryCbc(
    private val categorySbc: CategorySbc
) {

    // admin page
    @RequirePermission
    @PostMapping("/CATI001")
    fun retrieveCategory(@RequestBody body: MData): MResponse {
        return try {
            val res = categorySbc.retrieveListCategory(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    //for show category in home page
    @NotRequirePermission
    @PostMapping("/CATI002")
    fun retrieveListCategoryHome(): MResponse {
        return try {
            val res = categorySbc.retrieveListCategoryHome()
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    //for show category in categories page
    @NotRequirePermission
    @PostMapping("/CATI003")
    fun retrieveCategoryPage(@RequestBody body: MData): MResponse {
        return try {
            val res = categorySbc.retrieveListCategoryPage(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    //for show category summary and datasource summary
    @NotRequirePermission
    @PostMapping("/CATI004")
    fun retrieveCategorySummary(): MResponse {
        return try {
            val res = categorySbc.retrieveCategorySummary()
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/CATI005")
    fun retrieveCategoryDropdown(@RequestBody body: MData): MResponse {
        return try {
            val res = categorySbc.retrieveCategoryDropdown(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/CATR001")
    fun registerCategory(@RequestBody body: MData): MResponse {
        return try {
            val res = categorySbc.registerCategory(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/CATU001")
    fun updateCategory(@RequestBody body: MData): MResponse {

        val categoryId = body.getInt("categoryId")

        if (categoryId == 0) {
            return CommonMessageCode.MISSING_FIELDS.generateIMResponse(
                messages = setOf("CategoryId is required")
            )
        }

        return try {
            val res = categorySbc.updateCategory(body)
            CommonMessageCode.SUCCESS.generateIMResponse(res)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }
}