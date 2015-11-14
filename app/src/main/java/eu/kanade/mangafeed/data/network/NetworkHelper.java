package eu.kanade.mangafeed.data.network;


import com.squareup.okhttp.CacheControl;
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

    private OkHttpClient mClient;
    private CookieManager cookieManager;

    public final CacheControl NULL_CACHE_CONTROL = new CacheControl.Builder().noCache().build();
    public final Headers NULL_HEADERS = new Headers.Builder().build();

    public NetworkHelper() {
        mClient = new OkHttpClient();
        cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        mClient.setCookieHandler(cookieManager);
    }

    public Observable<Response> getResponse(final String url, final Headers headers, final CacheControl cacheControl) {
        return Observable.<Response>create(subscriber -> {
            try {
                if (!subscriber.isUnsubscribed()) {
                    Request request = new Request.Builder()
                            .url(url)
                            .cacheControl(cacheControl != null ? cacheControl : NULL_CACHE_CONTROL)
                            .headers(headers != null ? headers : NULL_HEADERS)
                            .build();
                    subscriber.onNext(mClient.newCall(request).execute());
                }
                subscriber.onCompleted();
            } catch (Throwable e) {
                subscriber.onError(e);
            }
        }).retry(3);
    }

    public Observable<String> mapResponseToString(final Response response) {
        return Observable.create(subscriber -> {
            try {
                subscriber.onNext(response.body().string());
                subscriber.onCompleted();
            } catch (Throwable e) {
                subscriber.onError(e);
            }
        });
    }

    public Observable<String> getStringResponse(final String url, final Headers headers, final CacheControl cacheControl) {

        return getResponse(url, headers, cacheControl)
                .flatMap(this::mapResponseToString);
    }

    public Observable<Response> postData(final String url, final RequestBody formBody, final Headers headers) {
        return Observable.create(subscriber -> {
            try {
                if (!subscriber.isUnsubscribed()) {
                    Request request = new Request.Builder()
                            .url(url)
                            .post(formBody)
                            .headers(headers != null ? headers : NULL_HEADERS)
                            .build();
                    subscriber.onNext(mClient.newCall(request).execute());
                }
                subscriber.onCompleted();
            } catch (Throwable e) {
                subscriber.onError(e);
            }
        });
    }

    public Observable<Response> getProgressResponse(final String url, final Headers headers, final ProgressListener listener) {
        return Observable.<Response>create(subscriber -> {
            try {
                if (!subscriber.isUnsubscribed()) {
                    Request request = new Request.Builder()
                            .url(url)
                            .cacheControl(NULL_CACHE_CONTROL)
                            .headers(headers != null ? headers : NULL_HEADERS)
                            .build();

                    OkHttpClient progressClient = mClient.clone();

                    progressClient.networkInterceptors().add(chain -> {
                        Response originalResponse = chain.proceed(chain.request());
                        return originalResponse.newBuilder()
                                .body(new ProgressResponseBody(originalResponse.body(), listener))
                                .build();
                    });
                    subscriber.onNext(progressClient.newCall(request).execute());
                }
                subscriber.onCompleted();
            } catch (Throwable e) {
                subscriber.onError(e);
            }
        }).retry(3);
    }

    public CookieStore getCookies() {
        return cookieManager.getCookieStore();
    }

}
