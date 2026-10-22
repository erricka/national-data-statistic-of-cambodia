package com.kh.niswebportal.core.util

import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Clean in-memory MultipartFile implementation for converted streams
 * that avoids dependencies on spring-test (MockMultipartFile).
 */
class ByteArrayMultipartFile(
    private val name: String,
    private val originalFilename: String,
    private val contentType: String?,
    private val content: ByteArray
) : MultipartFile {

    override fun getName(): String = name

    override fun getOriginalFilename(): String = originalFilename

    override fun getContentType(): String? = contentType

    override fun isEmpty(): Boolean = content.isEmpty()

    override fun getSize(): Long = content.size.toLong()

    override fun getBytes(): ByteArray = content.clone()

    override fun getInputStream(): InputStream = ByteArrayInputStream(content)

    override fun transferTo(dest: File) {
        FileOutputStream(dest).use { it.write(content) }
    }
}
