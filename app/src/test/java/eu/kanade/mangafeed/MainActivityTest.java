package eu.kanade.mangafeed;

/**
 * Created by len on 1/10/15.
 */
import android.os.Build;
import android.support.v7.widget.Toolbar;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import eu.kanade.mangafeed.ui.main.MainActivity;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class MainActivityTest {
    private MainActivity activity;

    // @Before => JUnit 4 annotation that specifies this method should run before each test is run
    // Useful to do setup for objects that are needed in the test
    @Before
    public void setup() {
        // Convenience method to run MainActivity through the Activity Lifecycle methods:
        // onCreate(...) => onStart() => onPostCreate(...) => onResume()
        activity = Robolectric.setupActivity(MainActivity.class);
    }

    @Test
    public void validate() {
        Toolbar toolbar = (Toolbar)activity.findViewById(R.id.toolbar);
        assertNotNull(toolbar);
    }
}
