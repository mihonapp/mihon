package eu.kanade.mangafeed.data.helpers;


import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import rx.Observable;

public final class NetworkHelper {

    private OkHttpClient mClient;

    public final CacheControl NULL_CACHE_CONTROL = new CacheControl.Builder().noCache().build();
    public final Headers NULL_HEADERS = new Headers.Builder().build();

    public NetworkHelper() {
        mClient = new OkHttpClient();
    }

    public Observable<Response> getResponse(final String url, final CacheControl cacheControl, final Headers headers) {
        return Observable.create(subscriber -> {
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
        });
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

    public Observable<String> getStringResponse(final String url, final CacheControl cacheControl, final Headers headers) {

        return getResponse(url, cacheControl, headers)
                .flatMap(this::mapResponseToString);
    }

}
