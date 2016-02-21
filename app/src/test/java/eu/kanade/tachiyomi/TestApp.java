package eu.kanade.tachiyomi;

import eu.kanade.tachiyomi.injection.component.DaggerAppComponent;
import eu.kanade.tachiyomi.injection.module.AppModule;
import eu.kanade.tachiyomi.injection.module.TestDataModule;

public class TestApp extends App {

    @Override
    protected DaggerAppComponent.Builder prepareAppComponent() {
        return DaggerAppComponent.builder()
                .appModule(new AppModule(this))
                .dataModule(new TestDataModule());
    }

    @Override
    protected void setupEventBus() {
        // Do nothing
    }

    @Override
    protected void setupAcra() {
        // Do nothing
    }
}
