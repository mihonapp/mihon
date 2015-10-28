package eu.kanade.mangafeed.data.helpers;


import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;

import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;
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

    private static class ProgressResponseBody extends ResponseBody {

        private final ResponseBody responseBody;
        private final ProgressListener progressListener;
        private BufferedSource bufferedSource;

        public ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
            this.responseBody = responseBody;
            this.progressListener = progressListener;
        }

        @Override public MediaType contentType() {
            return responseBody.contentType();
        }

        @Override public long contentLength() throws IOException {
            return responseBody.contentLength();
        }

        @Override public BufferedSource source() throws IOException {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }

        private Source source(Source source) {
            return new ForwardingSource(source) {
                long totalBytesRead = 0L;
                @Override public long read(Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    return bytesRead;
                }
            };
        }
    }

    public interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }

}
