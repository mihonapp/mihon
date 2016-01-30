package eu.kanade.tachiyomi.data.rest;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Release object
 * Contains information about the latest release
 */
public class Release {
    /**
     * Version name V0.0.0
     */
    @SerializedName("tag_name")
    private final String version;

    /** Change Log */
    @SerializedName("body")
    private final String log;

    /** Assets containing download url */
    @SerializedName("assets")
    private final List<Assets> assets;

    /**
     * Release constructor
     *
     * @param version version of latest release
     * @param log     log of latest release
     * @param assets  assets of latest release
     */
    public Release(String version, String log, List<Assets> assets) {
        this.version = version;
        this.log = log;
        this.assets = assets;
    }

    /**
     * Get latest release version
     *
     * @return latest release version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Get change log of latest release
     *
     * @return change log of latest release
     */
    public String getChangeLog() {
        return log;
    }

    /**
     * Get download link of latest release
     *
     * @return download link of latest release
     */
    public String getDownloadLink() {
        return assets.get(0).getDownloadLink();
    }

    /**
     * Assets class containing download url
     */
    class Assets {
        @SerializedName("browser_download_url")
        private final String download_url;


        /**
         * Assets Constructor
         *
         * @param download_url download url
         */
        @SuppressWarnings("unused") public Assets(String download_url) {
            this.download_url = download_url;
        }

        /**
         * Get download link of latest release
         *
         * @return download link of latest release
         */
        public String getDownloadLink() {
            return download_url;
        }
    }
}

