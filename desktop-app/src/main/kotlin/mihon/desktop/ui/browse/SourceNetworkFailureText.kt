package mihon.desktop.ui.browse

import androidx.compose.runtime.Composable
import mihon.desktop.i18n.recoveryText
import mihon.extension.ipc.NetworkFailure
import mihon.extension.ipc.NetworkFailureKind

@Composable
internal fun sourceNetworkFailureText(failure: NetworkFailure): String {
    val detail = when (failure.kind) {
        NetworkFailureKind.SITE_BLOCKED -> recoveryText(
            "The website has blocked access. Open its website to check. If it is blocked there too, access must be restored by the website; retrying here cannot remove the block.",
            "网站已封锁访问。可打开网页确认；如果网页也显示封锁，需要网站解除限制，应用重试无法解除。",
            "網站已封鎖存取。可開啟網頁確認；如果網頁也顯示封鎖，需要網站解除限制，應用程式重試無法解除。",
        )
        NetworkFailureKind.WEB_VERIFICATION -> recoveryText(
            "The website requires verification. Open its website, complete verification, then choose Done, retry.",
            "网站需要网页验证。请打开网页完成验证，再点击“完成并重试”。",
            "網站需要網頁驗證。請開啟網頁完成驗證，再點擊「完成並重試」。",
        )
        NetworkFailureKind.AUTHENTICATION_REQUIRED -> recoveryText(
            "The website denied access. Open its website to check whether sign-in is required.",
            "网站拒绝访问。请打开网页检查是否需要登录。",
            "網站拒絕存取。請開啟網頁檢查是否需要登入。",
        )
        NetworkFailureKind.RATE_LIMITED -> recoveryText(
            "The website is limiting requests. Please retry later.",
            "网站正在限制请求频率，请稍后重试。",
            "網站正在限制請求頻率，請稍後重試。",
        )
        else -> recoveryText("The website request failed.", "网站请求失败。", "網站請求失敗。")
    }
    return "$detail\n${failure.host} · HTTP ${failure.statusCode}"
}
