package com.kh.niswebportal.core.builder

import com.kh.niswebportal.core.com.MData


fun buildMData(
    appendMData: MData = MData(),
    cloneMData: MData = MData(),
    builderAction: MData.() -> Unit
): MData {
    return appendMData.appendMData { cloneMData }.apply(builderAction)
}

fun buildMData(map: Map<String, Any?>, builderAction: MData.() -> Unit): MData {

    return MData(map).apply(builderAction)

}