package com.kh.niswebportal.configuration.security

import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Component
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.util.Base64
import javax.crypto.Cipher

@Component
class RsaKeyProvider(private val session: HttpSession) {

    companion object {
        private const val RSA_PRIVATE_KEY_SESSION_ATTR = "SESSION_RSA_PRIVATE_KEY"
    }

    /**
     * Generates a key pair for the user's HTTP session and returns Base64 Public Key
     */
    val publicKeyBase64: String
        get() {
            val keyPairGen = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048)
            }
            val keyPair = keyPairGen.generateKeyPair()

            // Save private key in HTTP session
            session.setAttribute(RSA_PRIVATE_KEY_SESSION_ATTR, keyPair.private)

            return Base64.getEncoder().encodeToString(keyPair.public.encoded)
        }

    fun decrypt(encryptedBase64: String): String {
        val privateKey = session.getAttribute(RSA_PRIVATE_KEY_SESSION_ATTR) as? PrivateKey
            ?: throw IllegalStateException("RSA Private Key not found in current session. Call /auth/init first.")

        // Must use PKCS1Padding to match JSencrypt default
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        val decodedBytes = Base64.getDecoder().decode(encryptedBase64)
        return String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
    }
}