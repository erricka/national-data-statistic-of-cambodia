package com.kh.niswebportal.app.category

import com.kh.niswebportal.core.builder.buildMData
import com.kh.niswebportal.core.com.MData
import org.springframework.stereotype.Service
import kotlin.math.ceil

@Service
class CategorySbc(
    private val categoryEbc: CategoryEbc
) {
    fun retrieveListCategory(request: MData): MData {
        val page = request.getInt("page")
        val size = request.getInt("size")
        val list = categoryEbc.retrieveListCategory(request)
        val total = categoryEbc.countListCategory(request)
        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun registerCategory(body: MData): MData {
        val rows = categoryEbc.registerCategory(body)
        if (rows <= 0) throw RuntimeException("Insert failed")
        return buildMData {
            setBoolean("success", true)
        }
    }

    fun updateCategory(body: MData): MData {
        val rows = categoryEbc.updateCategory(body)
        return buildMData {
            setBoolean("success", true)
        }
    }

    fun retrieveListCategoryHome(): MData {
        val list = categoryEbc.retrieveListCategoryHome()
        return buildMData {
            set("list", list)
        }
    }

    fun retrieveListCategoryPage(request: MData): MData {
        val page = request.getInt("page")
        val size = request.getInt("size")
        val list = categoryEbc.retrieveListCategoryPage(request)
        val total = categoryEbc.countListCategoryPage(request)
        return buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun retrieveCategorySummary(): MData {
        val list = categoryEbc.retrieveCategorySummary()
        return buildMData {
            set("list", list)
        }
    }

    fun retrieveCategoryDropdown(request: MData): MData {
        val list = categoryEbc.retrieveCategoryDropdown(request)
        return buildMData {
            set("list", list)
        }
    }
}