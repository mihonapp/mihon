package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.injection.component.AppComponent
import eu.kanade.tachiyomi.injection.component.DaggerAppComponent
import eu.kanade.tachiyomi.injection.module.AppModule
import eu.kanade.tachiyomi.injection.module.TestDataModule

open class TestApp : App() {

    override fun createAppComponent(): AppComponent {
        return DaggerAppComponent.builder()
                .appModule(AppModule(this))
                .dataModule(TestDataModule())
                .build()
    }

    override fun setupAcra() {
        // Do nothing
    }
}
