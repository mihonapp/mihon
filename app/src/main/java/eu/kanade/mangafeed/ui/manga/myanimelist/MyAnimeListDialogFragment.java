package eu.kanade.mangafeed.ui.manga.myanimelist;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.ChapterSync;
import uk.co.ribot.easyadapter.EasyAdapter;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;

public class MyAnimeListDialogFragment extends DialogFragment {

    @Bind(R.id.myanimelist_search_field) EditText searchText;
    @Bind(R.id.myanimelist_search_button) Button searchButton;
    @Bind(R.id.myanimelist_search_results) ListView searchResults;

    private EasyAdapter<ChapterSync> adapter;
    private MyAnimeListFragment fragment;
    private ChapterSync selectedItem;

    public static MyAnimeListDialogFragment newInstance(MyAnimeListFragment parentFragment) {
        MyAnimeListDialogFragment dialog = new MyAnimeListDialogFragment();
        dialog.setParentFragment(parentFragment);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.dialog_myanimelist_search, null);
        ButterKnife.bind(this, view);


        builder.setView(view)
                .setPositiveButton(R.string.button_ok, (dialog, which) -> onPositiveButtonClick())
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> {});

        searchButton.setOnClickListener(v ->
                fragment.getPresenter().searchManga(searchText.getText().toString()));

        searchResults.setOnItemClickListener((parent, viewList, position, id) ->
                selectedItem = adapter.getItem(position));

        adapter = new EasyAdapter<>(getActivity(), ResultViewHolder.class);

        searchResults.setAdapter(adapter);

        return builder.create();
    }

    private void onPositiveButtonClick() {
        if (adapter != null && selectedItem != null) {
            fragment.getPresenter().registerManga(selectedItem);
        }
    }

    public void setResults(List<ChapterSync> results) {
        selectedItem = null;
        adapter.setItems(results);
    }

    public void setParentFragment(MyAnimeListFragment fragment) {
        this.fragment = fragment;
    }

    @LayoutId(R.layout.dialog_myanimelist_search_item)
    public static class ResultViewHolder extends ItemViewHolder<ChapterSync> {

        @ViewId(R.id.myanimelist_result_title) TextView title;

        public ResultViewHolder(View view) {
            super(view);
        }

        @Override
        public void onSetValues(ChapterSync chapter, PositionInfo positionInfo) {
            title.setText(chapter.title);
        }
    }

}
