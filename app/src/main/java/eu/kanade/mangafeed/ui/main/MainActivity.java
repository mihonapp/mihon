package eu.kanade.mangafeed.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.widget.FrameLayout;

import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.ui.setting.SettingsActivity;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;
import eu.kanade.mangafeed.ui.download.DownloadFragment;
import eu.kanade.mangafeed.ui.library.LibraryFragment;
import eu.kanade.mangafeed.ui.catalogue.SourceFragment;

public class MainActivity extends BaseActivity {

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.drawer_container)
    FrameLayout container;

    private Drawer drawer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        drawer = new DrawerBuilder()
                .withActivity(this)
                .withRootView(container)
                .withToolbar(toolbar)
                .withActionBarDrawerToggleAnimated(true)
                .addDrawerItems(
                        new PrimaryDrawerItem()
                                .withName(R.string.library_title)
                                .withIdentifier(R.id.nav_drawer_library),
//                        new PrimaryDrawerItem()
//                                .withName(R.string.recent_updates_title)
//                                .withIdentifier(R.id.nav_drawer_recent_updates),
                        new PrimaryDrawerItem()
                                .withName(R.string.catalogues_title)
                                .withIdentifier(R.id.nav_drawer_catalogues),
                        new PrimaryDrawerItem()
                                .withName(R.string.download_title)
                                .withIdentifier(R.id.nav_drawer_downloads),
                        new PrimaryDrawerItem()
                                .withName(R.string.settings_title)
                                .withIdentifier(R.id.nav_drawer_settings)
                                .withSelectable(false)
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
                                    case R.id.nav_drawer_recent_updates:
                                        break;
                                    case R.id.nav_drawer_catalogues:
                                        setFragment(SourceFragment.newInstance());
                                        break;
                                    case R.id.nav_drawer_downloads:
                                        setFragment(DownloadFragment.newInstance());
                                        break;
                                    case R.id.nav_drawer_settings:
                                        startActivity(new Intent(this, SettingsActivity.class));
                                        break;
                                }
                            }
                            return false;
                        }
                )
                .build();

        drawer.setSelection(R.id.nav_drawer_library);
    }

    public void setFragment(Fragment fragment) {
        try {
            if (fragment != null && getSupportFragmentManager() != null) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                if (ft != null) {
                    ft.replace(R.id.content_layout, fragment);
                    ft.commit();
                }
            }
        } catch (Exception e) {

        }
    }

}
