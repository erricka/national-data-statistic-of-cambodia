package com.kh.niswebportal.app.datasource

object Schemas {
    val FAM00001 = SourceSchema(
        "FAM00001", rules = arrayOf(
            FieldRule(0, "report_year", FieldType.NUMERIC),
            FieldRule(1, "report_month", FieldType.NUMERIC),
            FieldRule(2, "item_code", FieldType.TEXT),
            FieldRule(3, "current_amount", FieldType.AMOUNT),
            FieldRule(4, "budget_amount", FieldType.AMOUNT),
            FieldRule(6, "percentage", FieldType.NUMERIC, required = false),
        )
    )
}