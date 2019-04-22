package exh.ui.captcha

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.webkit.*
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.android.synthetic.main.eh_activity_captcha.*
import okhttp3.*
import rx.Single
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.net.URL
import java.util.*
import android.view.MotionEvent
import android.os.SystemClock
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.DelegatedHttpSource
import exh.util.melt
import rx.Observable
import java.io.Serializable
import kotlin.collections.HashMap

class BrowserActionActivity : AppCompatActivity() {
    private val sourceManager: SourceManager by injectLazy()
    private val preferencesHelper: PreferencesHelper by injectLazy()
    private val networkHelper: NetworkHelper by injectLazy()

    val httpClient = networkHelper.client
    private val jsonParser = JsonParser()

    private var currentLoopId: String? = null
    private var validateCurrentLoopId: String? = null
    private var strictValidationStartTime: Long? = null

    lateinit var credentialsObservable: Observable<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(eu.kanade.tachiyomi.R.layout.eh_activity_captcha)

        val sourceId = intent.getLongExtra(SOURCE_ID_EXTRA, -1)
        val originalSource = if(sourceId != -1L) sourceManager.get(sourceId) else null
        val source = if(originalSource != null) {
            originalSource as? ActionCompletionVerifier
                    ?: run {
                        (originalSource as? HttpSource)?.let {
                            NoopActionCompletionVerifier(it)
                        }
                    }
        } else null

        val headers = ((source as? HttpSource)?.headers?.toMultimap()?.mapValues {
            it.value.joinToString(",")
        } ?: emptyMap()) + (intent.getSerializableExtra(HEADERS_EXTRA) as? HashMap<String, String> ?: emptyMap())

        val cookies: HashMap<String, String>?
                = intent.getSerializableExtra(COOKIES_EXTRA) as? HashMap<String, String>
        val script: String? = intent.getStringExtra(SCRIPT_EXTRA)
        val url: String? = intent.getStringExtra(URL_EXTRA)
        val actionName = intent.getStringExtra(ACTION_NAME_EXTRA)

        val verifyComplete = if(source != null) {
            source::verifyComplete!!
        } else intent.getSerializableExtra(VERIFY_LAMBDA_EXTRA) as? (String) -> Boolean

        if(verifyComplete == null || url == null) {
            finish()
            return
        }

        val actionStr = actionName ?: "Solve captcha"

        toolbar.title = if(source != null) {
            "${source.name}: $actionStr"
        } else actionStr

        val parsedUrl = URL(url)

        val cm = CookieManager.getInstance()

        cookies?.forEach { (t, u) ->
            val cookieString = t + "=" + u + "; domain=" + parsedUrl.host
            cm.setCookie(url, cookieString)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            CookieSyncManager.createInstance(this).sync()

        webview.settings.javaScriptEnabled = true
        webview.settings.domStorageEnabled = true
        headers.entries.find { it.key.equals("user-agent", true) }?.let {
            webview.settings.userAgentString = it.value
        }

        var loadedInners = 0

        webview.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String, result: JsResult): Boolean {
                if(message.startsWith("exh-")) {
                    loadedInners++
                    // Wait for both inner scripts to be loaded
                    if(loadedInners >= 2) {
                        // Attempt to autosolve captcha
                        if(preferencesHelper.eh_autoSolveCaptchas().getOrDefault()
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            webview.post {
                                // 10 seconds to auto-solve captcha
                                strictValidationStartTime = System.currentTimeMillis() + 1000 * 10
                                beginSolveLoop()
                                beginValidateCaptchaLoop()
                                webview.evaluateJavascript(SOLVE_UI_SCRIPT_HIDE) {
                                    webview.evaluateJavascript(SOLVE_UI_SCRIPT_SHOW, null)
                                }
                            }
                        }
                    }
                    result.confirm()
                    return true
                }
                return false
            }
        }

        webview.webViewClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(actionName == null && preferencesHelper.eh_autoSolveCaptchas().getOrDefault()) {
                // Fetch auto-solve credentials early for speed
                credentialsObservable = httpClient.newCall(Request.Builder()
                        // Rob demo credentials
                        .url("https://speech-to-text-demo.ng.bluemix.net/api/v1/credentials")
                        .build())
                        .asObservableSuccess()
                        .subscribeOn(Schedulers.io())
                        .map {
                            val json = jsonParser.parse(it.body()!!.string())
                            it.close()
                            json["token"].string
                        }.melt()

                webview.addJavascriptInterface(this@BrowserActionActivity, "exh")
                AutoSolvingWebViewClient(this, verifyComplete, script, headers)
            } else {
                HeadersInjectingWebViewClient(this, verifyComplete, script, headers)
            }
        } else {
            BasicWebViewClient(this, verifyComplete, script)
        }

        webview.loadUrl(url, headers)

        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun captchaSolveFail() {
        currentLoopId = null
        validateCurrentLoopId = null
        Timber.e(IllegalStateException("Captcha solve failure!"))
        runOnUiThread {
            webview.evaluateJavascript(SOLVE_UI_SCRIPT_HIDE, null)
            MaterialDialog.Builder(this)
                    .title("Captcha solve failure")
                    .content("Failed to auto-solve the captcha!")
                    .cancelable(true)
                    .canceledOnTouchOutside(true)
                    .positiveText("Ok")
                    .show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @JavascriptInterface
    fun callback(result: String?, loopId: String, stage: Int) {
        if(loopId != currentLoopId) return

        when(stage) {
            STAGE_CHECKBOX -> {
                if(result!!.toBoolean()) {
                    webview.postDelayed({
                        getAudioButtonLocation(loopId)
                    }, 250)
                } else {
                    webview.postDelayed({
                        doStageCheckbox(loopId)
                    }, 250)
                }
            }
            STAGE_GET_AUDIO_BTN_LOCATION -> {
                if(result != null) {
                    val splitResult = result.split(" ").map { it.toFloat() }
                    val origX = splitResult[0]
                    val origY = splitResult[1]
                    val iw = splitResult[2]
                    val ih = splitResult[3]
                    val x = webview.x + origX / iw * webview.width
                    val y = webview.y + origY / ih * webview.height
                    Timber.d("Found audio button coords: %f %f", x, y)
                    simulateClick(x + 50, y + 50)
                    webview.post {
                        doStageDownloadAudio(loopId)
                    }
                } else {
                    webview.postDelayed({
                        getAudioButtonLocation(loopId)
                    }, 250)
                }
            }
            STAGE_DOWNLOAD_AUDIO -> {
                if(result != null) {
                    Timber.d("Got audio URL: $result")
                    performRecognize(result)
                            .observeOn(Schedulers.io())
                            .subscribe ({
                                Timber.d("Got audio transcript: $it")
                                webview.post {
                                    typeResult(loopId, it!!
                                            .replace(TRANSCRIPT_CLEANER_REGEX, "")
                                            .replace(SPACE_DEDUPE_REGEX, " ")
                                            .trim())
                                }
                            }, {
                                captchaSolveFail()
                            })
                } else {
                    webview.postDelayed({
                        doStageDownloadAudio(loopId)
                    }, 250)
                }
            }
            STAGE_TYPE_RESULT -> {
                if(result!!.toBoolean()) {
                    // Fail if captcha still not solved after 1.5s
                    strictValidationStartTime = System.currentTimeMillis() + 1500
                } else {
                    captchaSolveFail()
                }
            }
        }
    }

    fun performRecognize(url: String): Single<String> {
        return credentialsObservable.flatMap { token ->
            httpClient.newCall(Request.Builder()
                    .url(url)
                    .build()).asObservableSuccess().map {
                token to it
            }
        }.flatMap { (token, response) ->
            val audioFile = response.body()!!.bytes()

            httpClient.newCall(Request.Builder()
                    .url(HttpUrl.parse("https://stream.watsonplatform.net/speech-to-text/api/v1/recognize")!!
                            .newBuilder()
                            .addQueryParameter("watson-token", token)
                            .build())
                    .post(MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("jsonDescription", RECOGNIZE_JSON)
                            .addFormDataPart("audio.mp3",
                                    "audio.mp3",
                                    RequestBody.create(MediaType.parse("audio/mp3"), audioFile))
                            .build())
                    .build()).asObservableSuccess()
        }.map { response ->
            jsonParser.parse(response.body()!!.string())["results"][0]["alternatives"][0]["transcript"].string.trim()
        }.toSingle()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun doStageCheckbox(loopId: String) {
        if(loopId != currentLoopId) return

        webview.evaluateJavascript("""
            (function() {
                $CROSS_WINDOW_SCRIPT_OUTER

                let exh_cframe = document.querySelector('iframe[role=presentation][name|=a]');

                if(exh_cframe != null) {
                    cwmExec(exh_cframe, `
                        let exh_cb = document.getElementsByClassName('recaptcha-checkbox-checkmark')[0];
                        if(exh_cb != null) {
                            exh_cb.click();
                            return "true";
                        } else {
                            return "false";
                        }
                    `, function(result) {
                        exh.callback(result, '$loopId', $STAGE_CHECKBOX);
                    });
                } else {
                    exh.callback("false", '$loopId', $STAGE_CHECKBOX);
                }
            })();
        """.trimIndent().replace("\n", ""), null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getAudioButtonLocation(loopId: String) {
        webview.evaluateJavascript("""
            (function() {
                $CROSS_WINDOW_SCRIPT_OUTER

                let exh_bframe = document.querySelector("iframe[title='recaptcha challenge'][name|=c]");

                if(exh_bframe != null) {
                    let bfb = exh_bframe.getBoundingClientRect();
                    let iw = window.innerWidth;
                    let ih = window.innerHeight;
                    if(bfb.left < 0 || bfb.top < 0) {
                        exh.callback(null, '$loopId', $STAGE_GET_AUDIO_BTN_LOCATION);
                    } else {
                        cwmExec(exh_bframe, ` let exh_ab = document.getElementById("recaptcha-audio-button");
                            if(exh_ab != null) {
                                let bounds = exh_ab.getBoundingClientRect();
                                return (${'$'}{bfb.left} + bounds.left) + " " + (${'$'}{bfb.top} + bounds.top) + " " + ${'$'}{iw} + " " + ${'$'}{ih};
                            } else {
                                return null;
                            }
                        `, function(result) {
                            exh.callback(result, '$loopId', $STAGE_GET_AUDIO_BTN_LOCATION);
                        });
                    }
                } else {
                    exh.callback(null, '$loopId', $STAGE_GET_AUDIO_BTN_LOCATION);
                }
            })();
        """.trimIndent().replace("\n", ""), null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun doStageDownloadAudio(loopId: String) {
        webview.evaluateJavascript("""
            (function() {
                $CROSS_WINDOW_SCRIPT_OUTER

                let exh_bframe = document.querySelector("iframe[title='recaptcha challenge'][name|=c]");

                if(exh_bframe != null) {
                    cwmExec(exh_bframe, `
                        let exh_as = document.getElementById("audio-source");
                        if(exh_as != null) {
                            return exh_as.src;
                        } else {
                            return null;
                        }
                    `, function(result) {
                        exh.callback(result, '$loopId', $STAGE_DOWNLOAD_AUDIO);
                    });
                } else {
                    exh.callback(null, '$loopId', $STAGE_DOWNLOAD_AUDIO);
                }
            })();
        """.trimIndent().replace("\n", ""), null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun typeResult(loopId: String, result: String) {
        webview.evaluateJavascript("""
            (function() {
                $CROSS_WINDOW_SCRIPT_OUTER

                let exh_bframe = document.querySelector("iframe[title='recaptcha challenge'][name|=c]");

                if(exh_bframe != null) {
                    cwmExec(exh_bframe, `
                        let exh_as = document.getElementById("audio-response");
                        let exh_vb = document.getElementById("recaptcha-verify-button");
                        if(exh_as != null && exh_vb != null) {
                            exh_as.value = "$result";
                            exh_vb.click();
                            return "true";
                        } else {
                            return "false";
                        }
                    `, function(result) {
                        exh.callback(result, '$loopId', $STAGE_TYPE_RESULT);
                    });
                } else {
                    exh.callback("false", '$loopId', $STAGE_TYPE_RESULT);
                }
            })();
        """.trimIndent().replace("\n", ""), null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun beginSolveLoop() {
        val loopId = UUID.randomUUID().toString()
        currentLoopId = loopId
        doStageCheckbox(loopId)
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @JavascriptInterface
    fun validateCaptchaCallback(result: Boolean, loopId: String) {
        if(loopId != validateCurrentLoopId) return

        if(result) {
            Timber.d("Captcha solved!")
            webview.post {
                webview.evaluateJavascript(SOLVE_UI_SCRIPT_HIDE, null)
            }
            val asbtn = intent.getStringExtra(ASBTN_EXTRA)
            if(asbtn != null) {
                webview.post {
                    webview.evaluateJavascript("(function() {document.querySelector('$asbtn').click();})();", null)
                }
            }
        } else {
            val savedStrictValidationStartTime = strictValidationStartTime
            if(savedStrictValidationStartTime != null
                    && System.currentTimeMillis() > savedStrictValidationStartTime) {
                captchaSolveFail()
            } else {
                webview.postDelayed({
                    runValidateCaptcha(loopId)
                }, 250)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun runValidateCaptcha(loopId: String) {
        if(loopId != validateCurrentLoopId) return

        webview.evaluateJavascript("""
            (function() {
                $CROSS_WINDOW_SCRIPT_OUTER

                let exh_cframe = document.querySelector('iframe[role=presentation][name|=a]');

                if(exh_cframe != null) {
                    cwmExec(exh_cframe, `
                        let exh_cb = document.querySelector(".recaptcha-checkbox[aria-checked=true]");
                        if(exh_cb != null) {
                            return true;
                        } else {
                            return false;
                        }
                    `, function(result) {
                        exh.validateCaptchaCallback(result, '$loopId');
                    });
                } else {
                    exh.validateCaptchaCallback(false, '$loopId');
                }
            })();
        """.trimIndent().replace("\n", ""), null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun beginValidateCaptchaLoop() {
        val loopId = UUID.randomUUID().toString()
        validateCurrentLoopId = loopId
        runValidateCaptcha(loopId)
    }

    private fun simulateClick(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()
        val properties = arrayOfNulls<MotionEvent.PointerProperties>(1)
        val pp1 = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        properties[0] = pp1
        val pointerCoords = arrayOfNulls<MotionEvent.PointerCoords>(1)
        val pc1 = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        pointerCoords[0] = pc1
        var motionEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, 1, properties, pointerCoords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        dispatchTouchEvent(motionEvent)
        motionEvent.recycle()
        motionEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, 1, properties, pointerCoords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        dispatchTouchEvent(motionEvent)
        motionEvent.recycle()
    }

    companion object {
        const val VERIFY_LAMBDA_EXTRA = "verify_lambda_extra"
        const val SOURCE_ID_EXTRA = "source_id_extra"
        const val COOKIES_EXTRA = "cookies_extra"
        const val SCRIPT_EXTRA = "script_extra"
        const val URL_EXTRA = "url_extra"
        const val ASBTN_EXTRA = "asbtn_extra"
        const val ACTION_NAME_EXTRA = "action_name_extra"
        const val HEADERS_EXTRA = "headers_extra"

        const val STAGE_CHECKBOX = 0
        const val STAGE_GET_AUDIO_BTN_LOCATION = 1
        const val STAGE_DOWNLOAD_AUDIO = 2
        const val STAGE_TYPE_RESULT = 3

        val CROSS_WINDOW_SCRIPT_OUTER = """
            function cwmExec(element, code, cb) {
                console.log(">>> [CWM-Outer] Running: " + code);
                let runId = Math.random();
                if(cb != null) {
                    let listener;
                    listener = function(event) {
                        if(typeof event.data === "string" && event.data.startsWith("exh-")) {
                            let response = JSON.parse(event.data.substring(4));
                            if(response.id === runId) {
                                cb(response.result);
                                window.removeEventListener('message', listener);
                                console.log(">>> [CWM-Outer] Finished: " + response.id + " ==> " + response.result);
                            }
                        }
                    };
                    window.addEventListener('message', listener, false);
                }
                let runRequest = { id: runId, code: code };
                element.contentWindow.postMessage("exh-" + JSON.stringify(runRequest), "*");
            }
        """.trimIndent().replace("\n", "")

        val CROSS_WINDOW_SCRIPT_INNER = """
            window.addEventListener('message', function(event) {
                if(typeof event.data === "string" && event.data.startsWith("exh-")) {
                    let request = JSON.parse(event.data.substring(4));
                    console.log(">>> [CWM-Inner] Incoming: " + request.id);
                    let result = eval("(function() {" + request.code + "})();");
                    let response = { id: request.id, result: result };
                    console.log(">>> [CWM-Inner] Outgoing: " + response.id + " ==> " + response.result);
                    event.source.postMessage("exh-" + JSON.stringify(response), event.origin);
                }
            }, false);
            console.log(">>> [CWM-Inner] Loaded!");
            alert("exh-");
        """.trimIndent()

        val SOLVE_UI_SCRIPT_SHOW = """
            (function() {
                let exh_overlay = document.createElement("div");
                exh_overlay.id = "exh_overlay";
                exh_overlay.style.zIndex = 2000000001;
                exh_overlay.style.backgroundColor = "rgba(0, 0, 0, 0.8)";
                exh_overlay.style.position = "fixed";
                exh_overlay.style.top = 0;
                exh_overlay.style.left = 0;
                exh_overlay.style.width = "100%";
                exh_overlay.style.height = "100%";
                exh_overlay.style.pointerEvents = "none";
                document.body.appendChild(exh_overlay);
                let exh_otext = document.createElement("div");
                exh_otext.id = "exh_otext";
                exh_otext.style.zIndex = 2000000002;
                exh_otext.style.position = "fixed";
                exh_otext.style.top = "50%";
                exh_otext.style.left = 0;
                exh_otext.style.transform = "translateY(-50%)";
                exh_otext.style.color = "white";
                exh_otext.style.fontSize = "25pt";
                exh_otext.style.pointerEvents = "none";
                exh_otext.style.width = "100%";
                exh_otext.style.textAlign = "center";
                exh_otext.textContent = "Solving captcha..."
                document.body.appendChild(exh_otext);
            })();
        """.trimIndent()

        val SOLVE_UI_SCRIPT_HIDE = """
            (function() {
                let exh_overlay = document.getElementById("exh_overlay");
                let exh_otext = document.getElementById("exh_otext");
                if(exh_overlay != null) exh_overlay.remove();
                if(exh_otext != null) exh_otext.remove();
            })();
        """.trimIndent()

        val RECOGNIZE_JSON = """
            {
               "part_content_type": "audio/mp3",
               "keywords": [],
               "profanity_filter": false,
               "max_alternatives": 1,
               "speaker_labels": false,
               "firstReadyInSession": false,
               "preserveAdaptation": false,
               "timestamps": false,
               "inactivity_timeout": 30,
               "word_confidence": false,
               "audioMetrics": false,
               "latticeGeneration": true,
               "customGrammarWords": [],
               "action": "recognize"
            }
        """.trimIndent()

        val TRANSCRIPT_CLEANER_REGEX = Regex("[^0-9a-zA-Z_ -]")
        val SPACE_DEDUPE_REGEX = Regex(" +")

        private fun baseIntent(context: Context) =
                Intent(context, BrowserActionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

        fun launchCaptcha(context: Context,
                          source: ActionCompletionVerifier,
                          cookies: Map<String, String>,
                          script: String?,
                          url: String,
                          autoSolveSubmitBtnSelector: String? = null) {
            val intent = baseIntent(context).apply {
                putExtra(SOURCE_ID_EXTRA, source.id)
                putExtra(COOKIES_EXTRA, HashMap(cookies))
                putExtra(SCRIPT_EXTRA, script)
                putExtra(URL_EXTRA, url)
                putExtra(ASBTN_EXTRA, autoSolveSubmitBtnSelector)
            }

            context.startActivity(intent)
        }

        fun launchUniversal(context: Context,
                            source: HttpSource,
                            url: String) {
            val intent = baseIntent(context).apply {
                putExtra(SOURCE_ID_EXTRA, source.id)
                putExtra(URL_EXTRA, url)
            }

            context.startActivity(intent)
        }

        fun launchUniversal(context: Context,
                            sourceId: Long,
                            url: String) {
            val intent = baseIntent(context).apply {
                putExtra(SOURCE_ID_EXTRA, sourceId)
                putExtra(URL_EXTRA, url)
            }

            context.startActivity(intent)
        }

        fun launchAction(context: Context,
                         completionVerifier: ActionCompletionVerifier,
                         script: String?,
                         url: String,
                         actionName: String) {
            val intent = baseIntent(context).apply {
                putExtra(SOURCE_ID_EXTRA, completionVerifier.id)
                putExtra(SCRIPT_EXTRA, script)
                putExtra(URL_EXTRA, url)
                putExtra(ACTION_NAME_EXTRA, actionName)
            }

            context.startActivity(intent)
        }

        fun launchAction(context: Context,
                         completionVerifier: (String) -> Boolean,
                         script: String?,
                         url: String,
                         actionName: String,
                         headers: Map<String, String>? = emptyMap()) {
            val intent = baseIntent(context).apply {
                putExtra(HEADERS_EXTRA, HashMap(headers))
                putExtra(VERIFY_LAMBDA_EXTRA, completionVerifier as Serializable)
                putExtra(SCRIPT_EXTRA, script)
                putExtra(URL_EXTRA, url)
                putExtra(ACTION_NAME_EXTRA, actionName)
            }

            context.startActivity(intent)
        }
    }
}

class NoopActionCompletionVerifier(private val source: HttpSource): DelegatedHttpSource(source),
        ActionCompletionVerifier {
    override val versionId get() = source.versionId
    override val lang: String get() = source.lang

    override fun verifyComplete(url: String) = false
}

interface ActionCompletionVerifier : Source {
    fun verifyComplete(url: String): Boolean
}

