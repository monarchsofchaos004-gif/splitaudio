package app.splitaudio.adb

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 手寫最小 X.509 v3 自簽憑證（只有 JDK，不用 BouncyCastle：Android 系統內建的 BC 會搶先載入造成類別衝突）。
 *  ADB 只用憑證裡的公鑰；libadb-android 要的是 java.security.cert.Certificate。 */
object CertGen {
    private fun len(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
    }
    private fun tlv(tag: Int, body: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + len(body.size) + body
    private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun set(vararg parts: ByteArray) = tlv(0x31, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun int(v: BigInteger) = tlv(0x02, v.toByteArray())
    private fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    private fun time(d: Date): ByteArray {
        val year = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = d }.get(java.util.Calendar.YEAR)
        val utc = year < 2050   // RFC 5280：2049 年以前用 UTCTime，之後 GeneralizedTime
        val f = SimpleDateFormat(if (utc) "yyMMddHHmmss'Z'" else "yyyyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return tlv(if (utc) 0x17 else 0x18, f.format(d).toByteArray(Charsets.US_ASCII))
    }
    private val SHA256_RSA_ALG = seq(tlv(0x06, byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)), tlv(0x05, ByteArray(0)))
    private fun name(cn: String) = seq(set(seq(tlv(0x06, byteArrayOf(0x55, 0x04, 0x03)), utf8(cn))))

    /** 用 kp 自簽一張憑證，有效期 30 年 */
    fun selfSigned(kp: KeyPair, cn: String, now: Date = Date()): X509Certificate {
        val start = Date(now.time - 86_400_000L)
        val end = Date(now.time + 30L * 365 * 86_400_000L)
        val tbs = seq(
            tlv(0xA0, int(BigInteger.valueOf(2))),                        // version v3
            int(BigInteger(63, java.security.SecureRandom()).add(BigInteger.ONE)),   // serial
            SHA256_RSA_ALG, name(cn), seq(time(start), time(end)), name(cn),
            kp.public.encoded,                                           // SubjectPublicKeyInfo
        )
        val sig = java.security.Signature.getInstance("SHA256withRSA").run { initSign(kp.private); update(tbs); sign() }
        val der = seq(tbs, SHA256_RSA_ALG, tlv(0x03, byteArrayOf(0) + sig))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }
}
