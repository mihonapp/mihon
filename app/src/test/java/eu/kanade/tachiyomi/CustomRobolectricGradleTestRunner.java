package eu.kanade.tachiyomi;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.manifest.AndroidManifest;

public class CustomRobolectricGradleTestRunner
        extends RobolectricGradleTestRunner {

    public CustomRobolectricGradleTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    protected AndroidManifest getAppManifest(Config config) {
        AndroidManifest androidManifest = super.getAppManifest(config);
        androidManifest.setPackageName("eu.kanade.tachiyomi");
        return androidManifest;
    }
}