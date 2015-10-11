package eu.kanade.mangafeed.view;

import android.content.Context;
import android.content.Intent;

public interface BaseView {
    Context getActivity();
    void startActivity(Intent intent);
}
