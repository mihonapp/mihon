package exh;

import java.net.CookieManager;

import okhttp3.OkHttpClient;

public class NetworkManager {
    public static NetworkManager INSTANCE = null;
    public static NetworkManager getInstance() {
        if(INSTANCE == null) INSTANCE = new NetworkManager();
        return INSTANCE;
    }

    public NetworkManager() {}

    OkHttpClient httpClient = new OkHttpClient();
    private CookieManager cookieManager = new CookieManager();

    public OkHttpClient getClient() {
        return getHttpClient();
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public CookieManager getCookieManager() {
        return cookieManager;
    }
}
