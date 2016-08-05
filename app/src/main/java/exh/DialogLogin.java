package exh;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDialog;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.IOException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.concurrent.locks.ReentrantLock;

import eu.kanade.tachiyomi.R;
import okhttp3.Request;
import okhttp3.Response;

public class DialogLogin extends AppCompatDialog {

    public static ReentrantLock DIALOG_LOCK = new ReentrantLock();

    public DialogLogin(Context context) {
        super(context);
        setup();
    }

    public DialogLogin(Context context, int theme) {
        super(context, theme);
        setup();
    }

    protected DialogLogin(Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
        setup();
    }

    void setup() {
        setOnDismissListener(new OnDismissListener() {
            @Override public void onDismiss(DialogInterface dialog) {
                DIALOG_LOCK.unlock();
            }
        });
        setCancelable(false);
        setTitle("ExHentai Log-In");
    }

    /**
     * Requests a login.
     * <p/>
     * NOTE: THIS METHOD BLOCKS, DO NOT CALL FROM UI THREAD!
     */
    public static void requestLogin(final Context context) {
        if (!DIALOG_LOCK.isLocked()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    DialogLogin dialog = new DialogLogin(context);
                    dialog.show();
                    doKeepDialog(dialog);
                }
            });
            //Wait for the dialog to lock
            while (!DIALOG_LOCK.isLocked()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }
            //Wait for unlock
            DIALOG_LOCK.lock();
            DIALOG_LOCK.unlock();
        } else {
            Log.w("EHentai", "Login box lock held, waiting until unlocked...");
            DIALOG_LOCK.lock();
            DIALOG_LOCK.unlock();
        }
    }

    public static boolean isLoggedIn(final Context context, boolean useWeb) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String ehCookieString = prefs.getString("eh_cookie_string", "");
        if (ehCookieString.startsWith("ipb_member_id")) {
            if (useWeb) {
                //Perform further verification
                Request request = new Request.Builder().url("http://exhentai.org/img/b.png").header("Cookie", ehCookieString).build();
                Response response;
                try {
                    response = NetworkManager.getInstance().getClient().newCall(request).execute();
                } catch (IOException e) {
                    Log.e("EHentai", "Exception contacting ExHentai!", e);
                    return false;
                }
                return response.isSuccessful();
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    // Prevent dialog dismiss when orientation changes
    private static void doKeepDialog(Dialog dialog) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(lp);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DIALOG_LOCK.lock();
        setContentView(R.layout.activity_dialog_login);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        final WebView wv = (WebView) findViewById(R.id.webView);
        final DialogLogin instance = this;
        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                instance.dismiss();
            }
        });
        findViewById(R.id.btnAdvanced).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wv.loadUrl("http://exhentai.org/");
            }
        });
        super.onCreate(savedInstanceState);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setDomStorageEnabled(true);
        wv.loadUrl("https://forums.e-hentai.org/index.php?act=Login");
        wv.setWebViewClient(new WebViewClient() {
                                @Override
                                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                    view.loadUrl(url);

                                    return true;
                                }

                                @Override
                                public void onPageFinished(WebView view, String url) {
                                    super.onPageFinished(view, url);

                                    Log.i("EHentai", "Webview loaded: " + url);

                                    if (url.equals("https://forums.e-hentai.org/index.php?act=Login")) {
                                        //Hide distracting content
                                        view.loadUrl("javascript:(function () {document.getElementsByTagName('body')[0].style.visibility='hidden';" +
                                                "document.getElementsByName('submit')[0].style.visibility='visible';" +
                                                "document.querySelectorAll('td[width=\"60%\"][valign=\"top\"]')[0].style.visibility='visible';})()");
                                    } else if (url.startsWith("https://forums.e-hentai.org/index.php")
                                            && url.contains("act=Login")
                                            && url.contains("CODE=01")
                                            || url.equals("https://forums.e-hentai.org/index.php?")
                                            || url.equals("https://forums.e-hentai.org/index.php")) {
                                        String cookies = CookieManager.getInstance().getCookie(url);
                                        String[] cookieSplit = cookies.split(";");
                                        String memberID = null;
                                        String passHash = null;
                                        CookieStore cookieStore = NetworkManager.getInstance().getCookieManager().getCookieStore();
                                        URI uri = URI.create(url);
                                        for (String cookie : cookieSplit) {
                                            String trimmedCookie = cookie.trim();
                                            int equalIndex = trimmedCookie.indexOf("=");
                                            String key = trimmedCookie.substring(0, equalIndex);
                                            String value = trimmedCookie.substring(equalIndex + 1).replace("\"", "");
                                            HttpCookie newCookie = new HttpCookie(key, value);
                                            newCookie.setDomain(".e-hentai.org");

                                            cookieStore.add(uri, newCookie);

                                            if (key.equals("ipb_member_id")) {
                                                memberID = value;
                                            } else if (key.equals("ipb_pass_hash")) {
                                                passHash = value;
                                            }
                                        }

                                        if (memberID == null || passHash == null) {
                                            Toast.makeText(instance.getContext(), "Invalid login or captcha invalid, please try again!", Toast.LENGTH_SHORT).show();
                                            wv.loadUrl("https://forums.e-hentai.org/index.php?act=Login");
                                        } else {
                                            Log.i("EHentai", "Login OK, accessing ExHentai...");
                                            wv.loadUrl("http://exhentai.org/");
                                        }
                                    } else if (url.startsWith("http://exhentai.org") || url.startsWith("https://exhentai.org")) {
                                        String cookies = CookieManager.getInstance().getCookie(url);
                                        if(cookies == null) cookies = "";
                                        String[] cookieSplit = cookies.split(";");
                                        String memberID = null;
                                        String passHash = null;
                                        String igneous = null;
                                        CookieStore cookieStore = NetworkManager.getInstance().getCookieManager().getCookieStore();
                                        URI uri = URI.create(url);
                                        for (String cookie : cookieSplit) {
                                            String trimmedCookie = cookie.trim();
                                            if(trimmedCookie.isEmpty()) {
                                                continue;
                                            }
                                            int equalIndex = trimmedCookie.indexOf("=");
                                            if(equalIndex == -1) continue;
                                            String key = trimmedCookie.substring(0, equalIndex);
                                            String value = trimmedCookie.substring(equalIndex + 1).replace("\"", "");
                                            HttpCookie newCookie = new HttpCookie(key, value);
                                            newCookie.setDomain(".e-hentai.org");

                                            cookieStore.add(uri, newCookie);

                                            switch (key) {
                                                case "ipb_member_id":
                                                    memberID = value;
                                                    break;
                                                case "ipb_pass_hash":
                                                    passHash = value;
                                                    break;
                                                case "igneous":
                                                    igneous = value;
                                                    break;
                                            }
                                        }

                                        if (memberID != null && passHash != null && igneous != null) {
                                            Log.i("EHentai", "@ ExHentai and cookies are set, finalizing login...");
                                            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(instance.getContext());
                                            preferences.edit().putString("eh_cookie_string", "ipb_member_id=" + memberID + "; ipb_pass_hash=" + passHash + "; igneous=" + igneous + "; ").commit();
                                            instance.dismiss();
                                        } else {
                                            Log.i("EHentai", "@ ExHentai but cookies not fully set, waiting...");
                                        }
                                    }
                                }
                            }

        );
    }
}