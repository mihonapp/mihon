package eu.kanade.tachiyomi.injection.module;

import android.app.Application;

import org.mockito.Mockito;

import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.network.NetworkHelper;
import eu.kanade.tachiyomi.data.source.SourceManager;

public class TestDataModule extends DataModule {

    @Override
    DatabaseHelper provideDatabaseHelper(Application app) {
        return Mockito.mock(DatabaseHelper.class, Mockito.RETURNS_DEEP_STUBS);
    }

    @Override
    NetworkHelper provideNetworkHelper(Application app) {
        return Mockito.mock(NetworkHelper.class);
    }

    @Override
    SourceManager provideSourceManager(Application app) {
        return Mockito.mock(SourceManager.class, Mockito.RETURNS_DEEP_STUBS);
    }

}
