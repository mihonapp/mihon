package eu.kanade.tachiyomi.injection.module

import android.app.Application
import eu.kanade.tachiyomi.data.network.NetworkHelper
import eu.kanade.tachiyomi.data.source.SourceManager
import org.mockito.Mockito

class TestDataModule : DataModule() {

    override fun provideNetworkHelper(app: Application): NetworkHelper {
        return Mockito.mock(NetworkHelper::class.java)
    }

    override fun provideSourceManager(app: Application): SourceManager {
        return Mockito.mock(SourceManager::class.java, Mockito.RETURNS_DEEP_STUBS)
    }

}
