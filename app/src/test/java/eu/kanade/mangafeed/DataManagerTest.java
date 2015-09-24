package eu.kanade.mangafeed;


import android.database.Cursor;

import eu.kanade.mangafeed.data.local.DatabaseHelper;
import eu.kanade.mangafeed.data.local.Db;
import eu.kanade.mangafeed.data.local.PreferencesHelper;
import eu.kanade.mangafeed.data.model.Character;
import eu.kanade.mangafeed.data.remote.AndroidBoilerplateService;
import eu.kanade.mangafeed.util.DefaultConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import rx.Observable;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = DefaultConfig.EMULATE_SDK, manifest = DefaultConfig.MANIFEST)
public class DataManagerTest {

    private DataManager mDataManager;
    private AndroidBoilerplateService mMockAndroidBoilerplateService;
    private DatabaseHelper mDatabaseHelper;

    @Before
    public void setUp() {
        mMockAndroidBoilerplateService = mock(AndroidBoilerplateService.class);
        mDatabaseHelper = new DatabaseHelper(RuntimeEnvironment.application);
        mDatabaseHelper.clearTables().subscribe();
        mDataManager = new DataManager(mMockAndroidBoilerplateService,
                mDatabaseHelper,
                mock(Bus.class),
                new PreferencesHelper(RuntimeEnvironment.application),
                Schedulers.immediate());
    }

    @Test
    public void shouldSyncCharacters() throws Exception {
        int[] ids = new int[]{ 10034, 14050, 10435, 35093 };
        List<Character> characters = MockModelsUtil.createListOfMockCharacters(4);
        for (int i = 0; i < ids.length; i++) {
            when(mMockAndroidBoilerplateService.getCharacter(ids[i]))
                    .thenReturn(Observable.just(characters.get(i)));
        }

        TestSubscriber<Character> result = new TestSubscriber<>();
        mDataManager.syncCharacters(ids).subscribe(result);
        result.assertNoErrors();
        result.assertReceivedOnNext(characters);

        Cursor cursor = mDatabaseHelper.getBriteDb()
                .query("SELECT * FROM " + Db.CharacterTable.TABLE_NAME);
        assertEquals(4, cursor.getCount());
        cursor.close();
    }

}
