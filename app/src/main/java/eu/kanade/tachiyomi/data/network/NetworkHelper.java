package eu.kanade.tachiyomi.data.network;


import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;

import rx.Observable;

public final class NetworkHelper {

    private OkHttpClient client;
    private CookieManager cookieManager;

    public final CacheControl NULL_CACHE_CONTROL = new CacheControl.Builder().noCache().build();
    public final Headers NULL_HEADERS = new Headers.Builder().build();
    public final RequestBody NULL_REQUEST_BODY = new FormEncodingBuilder().build();

    public NetworkHelper() {
        client = new OkHttpClient();
        cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        client.setCookieHandler(cookieManager);
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

                OkHttpClient progressClient = client.clone();

                progressClient.networkInterceptors().add(chain -> {
                    Response originalResponse = chain.proceed(chain.request());
                    return originalResponse.newBuilder()
                            .body(new ProgressResponseBody(originalResponse.body(), listener))
                            .build();
                });
                return Observable.just(progressClient.newCall(request).execute());
            } catch (Throwable e) {
                return Observable.error(e);
            }
        }).retry(2);
    }

    public CookieStore getCookies() {
        return cookieManager.getCookieStore();
    }

}
