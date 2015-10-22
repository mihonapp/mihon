package eu.kanade.mangafeed.data.models;

public class Page {

    private int pageNumber;
    private String url;
    private String imageUrl;
    private String imagePath;

    public Page(int pageNumber, String url, String imageUrl, String imagePath) {
        this.pageNumber = pageNumber;
        this.url = url;
        this.imageUrl = imageUrl;
        this.imagePath = imagePath;
    }

    public Page(int pageNumber, String url) {
        this(pageNumber, url, null, null);
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

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
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
