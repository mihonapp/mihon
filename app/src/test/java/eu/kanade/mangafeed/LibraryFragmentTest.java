package eu.kanade.mangafeed;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


import static org.robolectric.util.FragmentTestUtil.startFragment;
import static org.junit.Assert.assertNotNull;

import eu.kanade.mangafeed.BuildConfig;
import eu.kanade.mangafeed.ui.fragment.LibraryFragment;
import eu.kanade.mangafeed.util.DefaultConfig;

/**
 * Created by len on 1/10/15.
 */

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = DefaultConfig.EMULATE_SDK)
public class LibraryFragmentTest {

    @Test
    public void mangaList_shouldNotBeEmpty() {
        LibraryFragment fragment = LibraryFragment.newInstance();
        startFragment(fragment);
        assertNotNull(fragment);
    }
}
