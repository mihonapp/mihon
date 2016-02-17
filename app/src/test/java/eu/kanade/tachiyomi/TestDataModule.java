package eu.kanade.tachiyomi;

import android.app.Application;

import org.mockito.Mockito;

import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.network.NetworkHelper;
import eu.kanade.tachiyomi.data.source.SourceManager;
import eu.kanade.tachiyomi.injection.module.DataModule;

public class TestDataModule extends DataModule {

    @Override
    public DatabaseHelper provideDatabaseHelper(Application app) {
        return Mockito.mock(DatabaseHelper.class, Mockito.RETURNS_DEEP_STUBS);
    }

    @Override
    public NetworkHelper provideNetworkHelper(Application app) {
        return Mockito.mock(NetworkHelper.class);
    }

    @Override
    public SourceManager provideSourceManager(Application app) {
        return Mockito.mock(SourceManager.class, Mockito.RETURNS_DEEP_STUBS);
    }

}
