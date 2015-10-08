package com.pushtorefresh.storio.sqlite.operations.post;

import android.support.annotation.Nullable;

/**
 * Created by len on 08/10/2015.
 */
public class PostResult {

    @Nullable
    private final Integer numberOfRowsUpdated;

    @Nullable
    private final Integer numberOfRowsInserted;

    @Nullable
    private final Integer numberOfRowsDeleted;

    public PostResult(Integer numberOfRowsUpdated, Integer numberOfRowsInserted, Integer numberOfRowsDeleted) {
        this.numberOfRowsUpdated = numberOfRowsUpdated;
        this.numberOfRowsInserted = numberOfRowsInserted;
        this.numberOfRowsDeleted = numberOfRowsDeleted;
    }

    @Nullable
    public Integer getNumberOfRowsUpdated() {
        return numberOfRowsUpdated;
    }

    @Nullable
    public Integer getNumberOfRowsInserted() {
        return numberOfRowsInserted;
    }

    @Nullable
    public Integer getNumberOfRowsDeleted() {
        return numberOfRowsDeleted;
    }
}
