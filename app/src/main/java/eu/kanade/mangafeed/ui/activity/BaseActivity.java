package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.AppComponent;

public class BaseActivity extends AppCompatActivity {

    protected void setupToolbar(Toolbar toolbar) {
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public void setToolbarTitle(String title) {
        getSupportActionBar().setTitle(title);
    }

    protected AppComponent applicationComponent() {
        return App.get(this).getComponent();
    }

    public Context getActivity() {
        return this;
    }
}
