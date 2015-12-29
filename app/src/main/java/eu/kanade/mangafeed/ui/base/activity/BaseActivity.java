package eu.kanade.mangafeed.ui.base.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import de.greenrobot.event.EventBus;
import icepick.Icepick;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Icepick.restoreInstanceState(this, savedState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

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
        registerForStickyEvents(0);
    }

    public void registerForStickyEvents(int priority) {
        EventBus.getDefault().registerSticky(this, priority);
    }

    public void registerForEvents() {
        registerForEvents(0);
    }

    public void registerForEvents(int priority) {
        EventBus.getDefault().register(this, priority);
    }

    public void unregisterForEvents() {
        EventBus.getDefault().unregister(this);
    }

}