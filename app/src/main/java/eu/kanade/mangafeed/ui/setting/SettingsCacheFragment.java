package eu.kanade.mangafeed.ui.setting;

import android.os.Bundle;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.cache.CacheManager;
import eu.kanade.mangafeed.ui.setting.preference.IntListPreference;
import eu.kanade.mangafeed.util.ToastUtil;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class SettingsCacheFragment extends SettingsNestedFragment implements Preference.OnPreferenceClickListener {

    private CacheManager cacheManager;
    private Preference clearCache;
    private Subscription clearChapterCacheSubscription;

    public static SettingsNestedFragment newInstance(int resourcePreference, int resourceTitle) {
        SettingsNestedFragment fragment = new SettingsCacheFragment();
        fragment.setArgs(resourcePreference, resourceTitle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = super.onCreateView(inflater, container, savedState);

        cacheManager = getSettingsActivity().cacheManager;

        IntListPreference cacheSize = (IntListPreference)findPreference(getString(R.string.pref_chapter_cache_size_key));
        cacheSize.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    cacheManager.setSize(Integer.parseInt(newValue.toString()));
                    return true;
                });

        clearCache = findPreference(getString(R.string.pref_clear_chapter_cache_key));
        clearCache.setOnPreferenceClickListener(this);
        clearCache.setSummary(getString(R.string.used_cache, cacheManager.getReadableSize()));

        return view;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.equals(clearCache)) {
            clearChapterCache();
        }
        return true;
    }

    private void clearChapterCache() {
        final AtomicInteger deletedFiles = new AtomicInteger();

        File[] files = cacheManager.getCacheDir().listFiles();

        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .title(R.string.deleting)
                .progress(false, files.length, true)
                .cancelable(false)
                .dismissListener(d -> {
                    if (clearChapterCacheSubscription != null && !clearChapterCacheSubscription.isUnsubscribed())
                        clearChapterCacheSubscription.unsubscribe();
                })
                .show();

        clearChapterCacheSubscription = Observable.defer(() -> Observable.from(files))
                .concatMap(file -> {
                    if (cacheManager.remove(file.getName())) {
                        deletedFiles.incrementAndGet();
                    }
                    return Observable.just(file);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(file -> dialog.incrementProgress(1),
                        error -> {
                            dialog.dismiss();
                            ToastUtil.showShort(getActivity(), getString(R.string.cache_delete_error));
                        }, () -> {
                            dialog.dismiss();
                            ToastUtil.showShort(getActivity(), getString(R.string.cache_deleted, deletedFiles.get()));
                            clearCache.setSummary(getString(R.string.used_cache, cacheManager.getReadableSize()));
                        });
    }

}
