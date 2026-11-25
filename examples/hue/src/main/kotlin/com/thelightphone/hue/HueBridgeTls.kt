package com.thelightphone.hue

import okhttp3.OkHttpClient
import org.bouncycastle.asn1.ASN1String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * The local API uses Hue's bridge roots and the bridge ID in the leaf certificate's common name.
 * The IP address is only the route to that identity.
 *
 * The PEMs below are Signify's publicly published Hue bridge root certificates.
 */
internal object HueBridgeTls {
    // "Philips Hue / root-bridge" — original bridge root CA.
    private const val ROOT_BRIDGE_PEM = """-----BEGIN CERTIFICATE-----
MIICMjCCAdigAwIBAgIUO7FSLbaxikuXAljzVaurLXWmFw4wCgYIKoZIzj0EAwIw
OTELMAkGA1UEBhMCTkwxFDASBgNVBAoMC1BoaWxpcHMgSHVlMRQwEgYDVQQDDAty
b290LWJyaWRnZTAiGA8yMDE3MDEwMTAwMDAwMFoYDzIwMzgwMTE5MDMxNDA3WjA5
MQswCQYDVQQGEwJOTDEUMBIGA1UECgwLUGhpbGlwcyBIdWUxFDASBgNVBAMMC3Jv
b3QtYnJpZGdlMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEjNw2tx2AplOf9x86
aTdvEcL1FU65QDxziKvBpW9XXSIcibAeQiKxegpq8Exbr9v6LBnYbna2VcaK0G22
jOKkTqOBuTCBtjAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBhjAdBgNV
HQ4EFgQUZ2ONTFrDT6o8ItRnKfqWKnHFGmQwdAYDVR0jBG0wa4AUZ2ONTFrDT6o8
ItRnKfqWKnHFGmShPaQ7MDkxCzAJBgNVBAYTAk5MMRQwEgYDVQQKDAtQaGlsaXBz
IEh1ZTEUMBIGA1UEAwwLcm9vdC1icmlkZ2WCFDuxUi22sYpLlwJY81Wrqy11phcO
MAoGCCqGSM49BAMCA0gAMEUCIEBYYEOsa07TH7E5MJnGw557lVkORgit2Rm1h3B2
sFgDAiEA1Fj/C3AN5psFMjo0//mrQebo0eKd3aWRx+pQY08mk48=
-----END CERTIFICATE-----"""

    // "Signify Hue / Hue Root CA 01" — newer bridge root CA.
    private const val HUE_ROOT_CA_01_PEM = """-----BEGIN CERTIFICATE-----
MIIBzDCCAXOgAwIBAgICEAAwCgYIKoZIzj0EAwIwPDELMAkGA1UEBhMCTkwxFDAS
BgNVBAoMC1NpZ25pZnkgSHVlMRcwFQYDVQQDDA5IdWUgUm9vdCBDQSAwMTAgFw0y
NTAyMjUwMDAwMDBaGA8yMDUwMTIzMTIzNTk1OVowPDELMAkGA1UEBhMCTkwxFDAS
BgNVBAoMC1NpZ25pZnkgSHVlMRcwFQYDVQQDDA5IdWUgUm9vdCBDQSAwMTBZMBMG
ByqGSM49AgEGCCqGSM49AwEHA0IABFfOO0jfSAUXGQ9kjEDzyBrcMQ3ItyA5krE+
cyvb1Y3xFti7KlAad8UOnAx0FBLn7HZrlmIwm1QnX0fK3LPM13mjYzBhMB0GA1Ud
DgQWBBTF1pSpsCASX/z0VHLigxU2CAaqoTAfBgNVHSMEGDAWgBTF1pSpsCASX/z0
VHLigxU2CAaqoTAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjAKBggq
hkjOPQQDAgNHADBEAiAk7duT+IHbOGO4UUuGLAEpyYejGZK9Z7V9oSfnvuQ5BQIg
IYSgwwxHXm73/JgcU9lAM6c8Bmu3UE3kBIUwBs1qXFw=
-----END CERTIFICATE-----"""

    private val client by lazy {
        val trustManager = buildTrustManager(listOf(ROOT_BRIDGE_PEM, HUE_ROOT_CA_01_PEM))
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun bridgeClient(expectedBridgeId: String?): OkHttpClient = client.newBuilder()
        .hostnameVerifier { _, session ->
            runCatching {
                val certificate = session.peerCertificates.firstOrNull() as? X509Certificate
                certificate != null && matchesBridgeIdentity(certificate.subjectX500Principal, expectedBridgeId)
            }.getOrDefault(false)
        }
        .build()

    private fun buildTrustManager(pems: List<String>): X509TrustManager {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        pems.forEachIndexed { index, pem ->
            val certificate = ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)).use { stream ->
                certificateFactory.generateCertificate(stream) as X509Certificate
            }
            keyStore.setCertificateEntry("hue-$index", certificate)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}

private val BRIDGE_ID = Regex("[0-9a-fA-F]{16}")

internal fun isHueBridgeId(value: String): Boolean = BRIDGE_ID.matches(value)

internal fun matchesBridgeIdentity(subject: X500Principal, expectedBridgeId: String?): Boolean {
    val commonNames = X500Name.getInstance(subject.encoded).getRDNs(BCStyle.CN)
        .flatMap { it.typesAndValues.asList() }
        .filter { it.type == BCStyle.CN }
    val certificateId = (commonNames.singleOrNull()?.value as? ASN1String)?.string ?: return false
    return isHueBridgeId(certificateId) &&
        (expectedBridgeId == null || certificateId.equals(expectedBridgeId, ignoreCase = true))
}
