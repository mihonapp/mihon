package eu.kanade.tachiyomi.data.rest;

import retrofit.http.GET;
import rx.Observable;


/**
 * Used to connect with the Github API
 */
public interface GithubService {
    String SERVICE_ENDPOINT = "https://api.github.com";

    @GET("/repos/inorichi/tachiyomi/releases/latest") Observable<Release> getLatestVersion();

}