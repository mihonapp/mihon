package eu.kanade.mangafeed.data.models;

public class Page {

    private int pageNumber;
    private String url;
    private String imageUrl;

    public Page(int pageNumber, String url, String imageUrl) {
        this.pageNumber = pageNumber;
        this.url = url;
        this.imageUrl = imageUrl;
    }

    public Page(int pageNumber, String url) {
        this(pageNumber, url, null);
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getUrl() {
        return url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        return "Page{" +
                "pageNumber=" + pageNumber +
                ", url='" + url + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                '}';
    }
}
