package mihon.telemetry

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

internal fun PackageInfo.getCertificateFingerprints(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = signingInfo!!
        if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
    } else {
        @Suppress("DEPRECATION")
        signatures
    }
        ?.map(Signature::getCertificateFingerprint)
        ?.toList()
        ?: emptyList()
}

internal val SignatureFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    PackageManager.GET_SIGNING_CERTIFICATES
} else {
    @Suppress("DEPRECATION")
    PackageManager.GET_SIGNATURES
}

@OptIn(ExperimentalStdlibApi::class)
private val CertificateFingerprintHexFormat = HexFormat {
    upperCase = true
    bytes {
        byteSeparator = ":"
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun Signature.getCertificateFingerprint(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .toHexString(CertificateFingerprintHexFormat)
}
