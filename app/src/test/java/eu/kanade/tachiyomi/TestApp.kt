package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.injection.component.DaggerAppComponent
import eu.kanade.tachiyomi.injection.module.AppModule
import eu.kanade.tachiyomi.injection.module.TestDataModule

open class TestApp : App() {

    override fun prepareAppComponent(): DaggerAppComponent.Builder {
        return DaggerAppComponent.builder()
                .appModule(AppModule(this))
                .dataModule(TestDataModule())
    }

    override fun setupAcra() {
        // Do nothing
    }
}
