package eu.kanade.mangafeed.ui.setting;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;

public class SettingsActivity extends BaseActivity {

    @Bind(R.id.toolbar) Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        if (savedInstanceState == null)
            getFragmentManager().beginTransaction().replace(R.id.settings_content,
                    new SettingsMainFragment())
                    .commit();
    }

    @Override
    public void onBackPressed() {
        if( !getFragmentManager().popBackStackImmediate() ) super.onBackPressed();
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

}
