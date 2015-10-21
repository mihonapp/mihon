package eu.kanade.mangafeed.ui.activity.base;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

public class BaseActivity extends AppCompatActivity {

    protected void setupToolbar(Toolbar toolbar) {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public void setToolbarTitle(String title) {
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(title);
    }

    public void setToolbarTitle(int titleResource) {
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(getString(titleResource));
    }

    public Context getActivity() {
        return this;
    }
}