package eu.kanade.mangafeed.ui.base.activity;

import android.content.Context;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import de.greenrobot.event.EventBus;

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

    public void setToolbarSubtitle(String title) {
        if (getSupportActionBar() != null)
            getSupportActionBar().setSubtitle(title);
    }

    public void setToolbarSubtitle(int titleResource) {
        if (getSupportActionBar() != null)
            getSupportActionBar().setSubtitle(getString(titleResource));
    }

    public void setToolbarElevation(int elevation) {
        if (getSupportActionBar() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            getSupportActionBar().setElevation(elevation);
    }

    public Context getActivity() {
        return this;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void registerForStickyEvents() {
        EventBus.getDefault().registerSticky(this);
    }

    public void registerForEvents() {
        EventBus.getDefault().register(this);
    }

    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }

}