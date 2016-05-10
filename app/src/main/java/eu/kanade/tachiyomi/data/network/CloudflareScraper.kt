package eu.kanade.tachiyomi.data.network

import android.net.Uri
import com.squareup.duktape.Duktape
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

object CloudflareScraper {

    //language=RegExp
    private val operationPattern = Regex("""setTimeout\(function\(\)\{\s+(var t,r,a,f.+?\r?\n[\s\S]+?a\.value =.+?)\r?\n""")

    //language=RegExp
    private val passPattern = Regex("""name="pass" value="(.+?)"""")

    //language=RegExp
    private val challengePattern = Regex("""name="jschl_vc" value="(\w+)"""")

    fun request(chain: Interceptor.Chain, cookies: PersistentCookieStore): Response {
        val response = chain.proceed(chain.request())

        // Check if we already solved a challenge
        if (response.code() != 502 &&
                cookies.get(response.request().url()).find { it.name() == "cf_clearance" } != null) {
            return response
        }

        // Check if Cloudflare anti-bot is on
        if ("URL=/cdn-cgi/" in response.header("Refresh", "")
                && response.header("Server", "") == "cloudflare-nginx") {
            return chain.proceed(resolveChallenge(response))
        }

        return response
    }

    private fun resolveChallenge(response: Response): Request {
        val duktape = Duktape.create()
        try {
            val originalRequest = response.request()
            val domain = originalRequest.url().host()
            val content = response.body().string()

            // CloudFlare requires waiting 5 seconds before resolving the challenge
            Thread.sleep(5000)

            val operation = operationPattern.find(content)?.groups?.get(1)?.value
            val challenge = challengePattern.find(content)?.groups?.get(1)?.value
            val pass = passPattern.find(content)?.groups?.get(1)?.value

            if (operation == null || challenge == null || pass == null) {
                throw RuntimeException("Failed resolving Cloudflare challenge")
            }

            val js = operation
                    //language=RegExp
                    .replace(Regex("""a\.value =(.+?) \+ .+?;"""), "$1")
                    //language=RegExp
                    .replace(Regex("""\s{3,}[a-z](?: = |\.).+"""), "")
                    .replace("\n", "")

            // Duktape can only return strings, so the result has to be converted to string first
            val result = duktape.evaluate("$js.toString()").toInt()

            val answer = "${result + domain.length}"

            val url = Uri.parse("http://$domain/cdn-cgi/l/chk_jschl").buildUpon()
                    .appendQueryParameter("jschl_vc", challenge)
                    .appendQueryParameter("pass", pass)
                    .appendQueryParameter("jschl_answer", answer)
                    .toString()

            val referer = originalRequest.url().toString()
            return get(url, originalRequest.headers().newBuilder().add("Referer", referer).build())
        } finally {
            duktape.close()
        }
    }

}