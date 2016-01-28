package eu.kanade.tachiyomi.data.network;


import android.content.Context;

import java.io.File;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rx.Observable;

public final class NetworkHelper {

    private OkHttpClient client;

    private CookieManager cookieManager;

    public final CacheControl NULL_CACHE_CONTROL = new CacheControl.Builder().noCache().build();
    public final Headers NULL_HEADERS = new Headers.Builder().build();
    public final RequestBody NULL_REQUEST_BODY = new FormBody.Builder().build();

    private static final int CACHE_SIZE = 5 * 1024 * 1024; // 5 MiB
    private static final String CACHE_DIR_NAME = "network_cache";

    public NetworkHelper(Context context) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);

        cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .cache(new Cache(cacheDir, CACHE_SIZE))
                .build();
    }

    public Observable<Response> getResponse(final String url, final Headers headers, final CacheControl cacheControl) {
        return Observable.defer(() -> {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .cacheControl(cacheControl != null ? cacheControl : NULL_CACHE_CONTROL)
                        .headers(headers != null ? headers : NULL_HEADERS)
                        .build();

                return Observable.just(client.newCall(request).execute());
            } catch (Throwable e) {
                return Observable.error(e);
            }
        }).retry(1);
    }

    public Observable<String> mapResponseToString(final Response response) {
        return Observable.defer(() -> {
            try {
                return Observable.just(response.body().string());
            } catch (Throwable e) {
                return Observable.error(e);
            }
        });
    }

    public Observable<String> getStringResponse(final String url, final Headers headers, final CacheControl cacheControl) {
        return getResponse(url, headers, cacheControl)
                .flatMap(this::mapResponseToString);
    }

    public Observable<Response> postData(final String url, final RequestBody formBody, final Headers headers) {
        return Observable.defer(() -> {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .post(formBody != null ? formBody : NULL_REQUEST_BODY)
                        .headers(headers != null ? headers : NULL_HEADERS)
                        .build();
                return Observable.just(client.newCall(request).execute());
            } catch (Throwable e) {
                return Observable.error(e);
            }
        }).retry(1);
    }

    public Observable<Response> getProgressResponse(final String url, final Headers headers, final ProgressListener listener) {
        return Observable.defer(() -> {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .cacheControl(NULL_CACHE_CONTROL)
                        .headers(headers != null ? headers : NULL_HEADERS)
                        .build();

                OkHttpClient progressClient = client.newBuilder()
                        .cache(null)
                        .addNetworkInterceptor(chain -> {
                            Response originalResponse = chain.proceed(chain.request());
                            return originalResponse.newBuilder()
                                    .body(new ProgressResponseBody(originalResponse.body(), listener))
                                    .build();
                        }).build();

                return Observable.just(progressClient.newCall(request).execute());
            } catch (Throwable e) {
                return Observable.error(e);
            }
        }).retry(1);
    }

    public CookieStore getCookies() {
        return cookieManager.getCookieStore();
    }

}
