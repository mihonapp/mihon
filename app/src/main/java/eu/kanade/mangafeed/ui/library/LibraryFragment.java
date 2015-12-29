package eu.kanade.mangafeed.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.data.sync.LibraryUpdateService;
import eu.kanade.mangafeed.ui.base.activity.BaseActivity;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import eu.kanade.mangafeed.ui.library.category.CategoryFragment;
import eu.kanade.mangafeed.ui.main.MainActivity;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(LibraryPresenter.class)
public class LibraryFragment extends BaseRxFragment<LibraryPresenter>
        implements ActionMode.Callback {

    TabLayout tabs;
    AppBarLayout appBar;

    @Bind(R.id.view_pager) ViewPager categoriesPager;
    protected LibraryAdapter adapter;

    private ActionMode actionMode;

    public static LibraryFragment newInstance() {
        return new LibraryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library, container, false);
        setToolbarTitle(getString(R.string.label_library));
        ButterKnife.bind(this, view);

        appBar = ((MainActivity) getActivity()).getAppBar();
        tabs = (TabLayout) inflater.inflate(R.layout.tab_layout, appBar, false);
        appBar.addView(tabs);


        adapter = new LibraryAdapter(getChildFragmentManager());
        categoriesPager.setAdapter(adapter);

        return view;
    }

    @Override
    public void onDestroyView() {
        appBar.removeView(tabs);
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.library, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if (!LibraryUpdateService.isRunning(getActivity())) {
                    Intent intent = LibraryUpdateService.getStartIntent(getActivity());
                    getActivity().startService(intent);
                }
                return true;
            case R.id.action_edit_categories:
                onEditCategories();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onEditCategories() {
        Fragment fragment = CategoryFragment.newInstance();
        ((MainActivity) getActivity()).pushFragment(fragment);
    }

    public void onNextCategories(List<Category> categories) {
        List<Category> actualCategories = new ArrayList<>();

        Category defaultCat = Category.create("Default");
        defaultCat.id = 0;
        actualCategories.add(defaultCat);

        actualCategories.addAll(categories);
        adapter.setCategories(actualCategories);
        tabs.setupWithViewPager(categoriesPager);

        tabs.setVisibility(actualCategories.size() == 1 ? View.GONE : View.VISIBLE);
    }

    public void setContextTitle(int count) {
        actionMode.setTitle(getString(R.string.label_selected, count));
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.library_selection, menu);
        adapter.setSelectionMode(FlexibleAdapter.MODE_MULTI);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_move_to_category:
                moveMangasToCategories(getPresenter().selectedMangas);
                return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        adapter.setSelectionMode(FlexibleAdapter.MODE_SINGLE);
        getPresenter().selectedMangas.clear();
        actionMode = null;
    }

    public void destroyActionModeIfNeeded() {
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private void moveMangasToCategories(List<Manga> mangas) {
        new MaterialDialog.Builder(getActivity())
                .title(R.string.action_move_category)
                .items(getPresenter().getCategoriesNames())
                .itemsCallbackMultiChoice(null, (dialog, which, text) -> {
                    getPresenter().moveMangasToCategories(which, mangas);
                    destroyActionModeIfNeeded();
                    return true;
                })
                .positiveText(R.string.button_ok)
                .negativeText(R.string.button_cancel)
                .show();

    }

    @Nullable
    public ActionMode getActionMode() {
        return actionMode;
    }

    public LibraryAdapter getAdapter() {
        return adapter;
    }

    public void createActionModeIfNeeded() {
        if (actionMode == null) {
            actionMode = ((BaseActivity) getActivity()).startSupportActionMode(this);
        }
    }

    public void invalidateActionMode() {
        actionMode.invalidate();
    }

}
