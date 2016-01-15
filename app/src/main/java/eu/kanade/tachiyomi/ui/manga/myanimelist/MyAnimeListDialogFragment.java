package eu.kanade.tachiyomi.ui.manga.myanimelist;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.MangaSync;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;
import uk.co.ribot.easyadapter.EasyAdapter;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;

public class MyAnimeListDialogFragment extends DialogFragment {

    @Bind(R.id.myanimelist_search_field) EditText searchText;
    @Bind(R.id.myanimelist_search_results) ListView searchResults;
    @Bind(R.id.progress) ProgressBar progressBar;

    private EasyAdapter<MangaSync> adapter;
    private MangaSync selectedItem;

    private Subscription searchSubscription;

    public static MyAnimeListDialogFragment newInstance() {
        return new MyAnimeListDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedState) {
        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .customView(R.layout.dialog_myanimelist_search, false)
                .positiveText(R.string.button_ok)
                .negativeText(R.string.button_cancel)
                .onPositive((dialog1, which) -> onPositiveButtonClick())
                .build();

        ButterKnife.bind(this, dialog.getView());

        // Create adapter
        adapter = new EasyAdapter<>(getActivity(), ResultViewHolder.class);
        searchResults.setAdapter(adapter);

        // Set listeners
        searchResults.setOnItemClickListener((parent, viewList, position, id) ->
                selectedItem = adapter.getItem(position));

        // Do an initial search based on the manga's title
        if (savedState == null) {
            String title = getPresenter().manga.title;
            searchText.append(title);
            search(title);
        }

        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        PublishSubject<String> querySubject = PublishSubject.create();
        searchText.addTextChangedListener(new SimpleTextChangeListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                querySubject.onNext(s.toString());
            }
        });

        // Listen to text changes
        searchSubscription = querySubject.debounce(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::search);
    }

    @Override
    public void onPause() {
        if (searchSubscription != null) {
            searchSubscription.unsubscribe();
        }
        super.onPause();
    }

    private void onPositiveButtonClick() {
        if (adapter != null && selectedItem != null) {
            getPresenter().registerManga(selectedItem);
        }
    }

    private void search(String query) {
        if (!TextUtils.isEmpty(query)) {
            searchResults.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            getPresenter().searchManga(query);
        }
    }

    public void onSearchResults(List<MangaSync> results) {
        selectedItem = null;
        progressBar.setVisibility(View.GONE);
        searchResults.setVisibility(View.VISIBLE);
        adapter.setItems(results);
    }

    public void onSearchResultsError() {
        progressBar.setVisibility(View.GONE);
        searchResults.setVisibility(View.VISIBLE);
        adapter.getItems().clear();
    }

    public MyAnimeListFragment getMALFragment() {
        return (MyAnimeListFragment) getParentFragment();
    }

    public MyAnimeListPresenter getPresenter() {
        return getMALFragment().getPresenter();
    }

    @LayoutId(R.layout.dialog_myanimelist_search_item)
    public static class ResultViewHolder extends ItemViewHolder<MangaSync> {

        @ViewId(R.id.myanimelist_result_title) TextView title;

        public ResultViewHolder(View view) {
            super(view);
        }

        @Override
        public void onSetValues(MangaSync chapter, PositionInfo positionInfo) {
            title.setText(chapter.title);
        }
    }

    private static class SimpleTextChangeListener implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    }

}
