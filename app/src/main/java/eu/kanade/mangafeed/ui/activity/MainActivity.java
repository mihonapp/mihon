package eu.kanade.mangafeed.ui.activity;

import android.os.Bundle;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;

import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.helpers.DatabaseHelper;
import eu.kanade.mangafeed.ui.fragment.LibraryFragment;
import rx.subscriptions.CompositeSubscription;

public class MainActivity extends BaseActivity {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.drawer_container)
    FrameLayout container;

    @Inject DatabaseHelper mDb;
    private Drawer drawer;
    private CompositeSubscription mSubscriptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applicationComponent().inject(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mSubscriptions = new CompositeSubscription();

        setupToolbar();

        drawer = new DrawerBuilder()
                .withActivity(this)
                .withRootView(container)
                .withToolbar(toolbar)
                .withActionBarDrawerToggleAnimated(true)
                .addDrawerItems(
                        new PrimaryDrawerItem()
                                .withName(R.string.library_title)
                                .withIdentifier(R.id.nav_drawer_library),
                        new PrimaryDrawerItem()
                                .withName(R.string.recent_updates_title)
                                .withIdentifier(R.id.nav_drawer_recent_updates),
                        new PrimaryDrawerItem()
                                .withName(R.string.catalogues_title)
                                .withIdentifier(R.id.nav_drawer_catalogues),
                        new PrimaryDrawerItem()
                                .withName(R.string.settings_title)
                                .withIdentifier(R.id.nav_drawer_settings)
                )
                .withSavedInstance(savedInstanceState)
                .withOnDrawerItemClickListener(
                        (view, position, drawerItem) -> {
                            if (drawerItem != null) {
                                int identifier = drawerItem.getIdentifier();
                                switch (identifier) {
                                    case R.id.nav_drawer_library:
                                        setFragment(LibraryFragment.newInstance());
                                        break;
                                    case R.id.nav_drawer_catalogues:
                                    case R.id.nav_drawer_recent_updates:
                                    case R.id.nav_drawer_settings:
                                        break;
                                }
                            }
                            return false;
                        }
                )
                .build();

        drawer.setSelection(R.id.nav_drawer_library);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSubscriptions.unsubscribe();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_github:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
    }

    private void setFragment(Fragment fragment) {
        try {
            if (fragment != null && getSupportFragmentManager() != null) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                if (ft != null) {
                    ft.replace(R.id.content_layout, fragment);
                    ft.commit();
                }
            }
        } catch (Exception e) {

        }
    }

}
