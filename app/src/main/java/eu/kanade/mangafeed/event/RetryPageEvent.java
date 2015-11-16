package eu.kanade.mangafeed.event;

import eu.kanade.mangafeed.data.source.model.Page;

public class RetryPageEvent {

    private Page page;

    public RetryPageEvent(Page page) {
        this.page = page;
    }

    public Page getPage() {
        return page;
    }

}
