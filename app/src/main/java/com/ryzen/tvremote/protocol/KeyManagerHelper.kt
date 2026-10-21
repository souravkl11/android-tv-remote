package com.ryzen.tvremote.protocol

import android.content.Context
import android.os.Build
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object KeyManagerHelper {
    private const val TAG = "KeyManagerHelper"
    private const val KEYSTORE_NAME = "tvremote_keystore.p12"
    private const val KEY_ALIAS = "tvremote_client"
    private val KEY_PASSWORD = "remote_secure_keystore_pass".toCharArray()
    private const val PREFS_NAME = "tvremote_keys"
    private const val KEY_PAIRED_PREFIX = "host_paired_"

    fun isHostPaired(context: Context, host: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PAIRED_PREFIX + host, false)
    }

    fun setHostPaired(context: Context, host: String, paired: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PAIRED_PREFIX + host, paired).apply()
    }

    fun getOrCreateSslContext(context: Context, clientName: String = "atvremote"): SSLContext {
        val keyStore = getOrCreateKeyStore(context, clientName)
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, KEY_PASSWORD)

        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(kmf.keyManagers, trustAll, SecureRandom())
        return sslContext
    }

    fun getClientCertificate(context: Context): X509Certificate {
        val keyStore = getOrCreateKeyStore(context)
        return keyStore.getCertificate(KEY_ALIAS) as X509Certificate
    }

    private fun getOrCreateKeyStore(context: Context, clientName: String = "atvremote"): KeyStore {
        val keyStore = KeyStore.getInstance("PKCS12")
        val file = File(context.filesDir, KEYSTORE_NAME)

        if (file.exists() && file.length() > 0) {
            try {
                FileInputStream(file).use { fis ->
                    keyStore.load(fis, KEY_PASSWORD)
                }
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    return keyStore
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load existing keystore, regenerating...", e)
                file.delete()
            }
        }

        // Generate new 2048-bit RSA KeyPair and Certificate
        keyStore.load(null, null)
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.generateKeyPair()
        val cert = generateSelfSignedCertificate(keyPair, clientName)

        keyStore.setKeyEntry(
            KEY_ALIAS,
            keyPair.private,
            KEY_PASSWORD,
            arrayOf(cert)
        )

        FileOutputStream(file).use { fos ->
            keyStore.store(fos, KEY_PASSWORD)
        }
        Log.i(TAG, "Generated and saved fresh client credentials to $KEYSTORE_NAME")
        return keyStore
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair, clientName: String): X509Certificate {
        val deviceName = Build.MODEL ?: "Android Phone"
        val issuer = X500Name("CN=$clientName ($deviceName)")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 24 * 3600 * 1000L) // Yesterday
        val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000L) // 10 years
        val subPubKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)

        val builder = X509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            issuer,
            subPubKeyInfo
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val holder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
        val bytes = ByteArray(clean.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            bytes[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return bytes
    }

    /**
     * Compute SHA-256 hash of client modulus + client exponent + server modulus + server exponent + PIN[2:]
     * as required by Android TV Remote v2 protocol.
     */
    fun computePairingSecret(
        clientCert: X509Certificate,
        serverCert: X509Certificate,
        pinCode: String
    ): ByteArray {
        val cleanPin = pinCode.trim().uppercase()
        if (cleanPin.length != 6) {
            throw IllegalArgumentException("PIN code must be exactly 6 characters")
        }

        val clientPub = clientCert.publicKey as RSAPublicKey
        val serverPub = serverCert.publicKey as RSAPublicKey

        val clientModulusHex = clientPub.modulus.toString(16).uppercase()
        val clientExponentHex = "0" + clientPub.publicExponent.toString(16).uppercase()
        val serverModulusHex = serverPub.modulus.toString(16).uppercase()
        val serverExponentHex = "0" + serverPub.publicExponent.toString(16).uppercase()
        val pinSuffixHex = cleanPin.substring(2)

        val md = MessageDigest.getInstance("SHA-256")
        md.update(hexToBytes(clientModulusHex))
        md.update(hexToBytes(clientExponentHex))
        md.update(hexToBytes(serverModulusHex))
        md.update(hexToBytes(serverExponentHex))
        md.update(hexToBytes(pinSuffixHex))

        val hashResult = md.digest()

        val expectedFirstByte = cleanPin.substring(0, 2).toInt(16)
        val actualFirstByte = hashResult[0].toInt() and 0xFF

        if (expectedFirstByte != actualFirstByte) {
            throw IllegalArgumentException("PIN verification failed: hash mismatch ($expectedFirstByte != $actualFirstByte)")
        }

        return hashResult
    }
}
