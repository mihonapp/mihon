package eu.kanade.tachiyomi.ui.library;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.materialdialogs.MaterialDialog;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Category;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.io.IOHandler;
import eu.kanade.tachiyomi.data.library.LibraryUpdateService;
import eu.kanade.tachiyomi.event.LibraryMangasEvent;
import eu.kanade.tachiyomi.ui.base.fragment.BaseRxFragment;
import eu.kanade.tachiyomi.ui.library.category.CategoryActivity;
import eu.kanade.tachiyomi.ui.main.MainActivity;
import eu.kanade.tachiyomi.util.ToastUtil;
import icepick.State;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(LibraryPresenter.class)
public class LibraryFragment extends BaseRxFragment<LibraryPresenter>
        implements ActionMode.Callback {


    private static final int REQUEST_IMAGE_OPEN = 101;

    protected LibraryAdapter adapter;

    @Bind(R.id.view_pager) ViewPager viewPager;

    @State int activeCategory;

    @State String query = "";

    private TabLayout tabs;

    private AppBarLayout appBar;

    private ActionMode actionMode;

    private Manga selectedCoverManga;

    public static LibraryFragment newInstance() {
        return new LibraryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_library, container, false);
        setToolbarTitle(getString(R.string.label_library));
        ButterKnife.bind(this, view);

        appBar = ((MainActivity) getActivity()).getAppBar();
        tabs = (TabLayout) inflater.inflate(R.layout.library_tab_layout, appBar, false);
        appBar.addView(tabs);

        adapter = new LibraryAdapter(getChildFragmentManager());
        viewPager.setAdapter(adapter);
        tabs.setupWithViewPager(viewPager);

        if (savedState != null) {
            getPresenter().searchSubject.onNext(query);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        appBar.removeView(tabs);
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        activeCategory = viewPager.getCurrentItem();
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.library, menu);

        // Initialize search menu
        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        if (!TextUtils.isEmpty(query)) {
            searchItem.expandActionView();
            searchView.setQuery(query, true);
            searchView.clearFocus();
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                onSearchTextChange(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                onSearchTextChange(newText);
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                LibraryUpdateService.start(getActivity());
                return true;
            case R.id.action_edit_categories:
                onEditCategories();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onSearchTextChange(String query) {
        this.query = query;
        getPresenter().searchSubject.onNext(query);
    }

    private void onEditCategories() {
        Intent intent = CategoryActivity.newIntent(getActivity());
        startActivity(intent);
    }

    public void onNextLibraryUpdate(List<Category> categories, Map<Integer, List<Manga>> mangas) {
        boolean hasMangasInDefaultCategory = mangas.get(0) != null;
        int activeCat = adapter.categories != null ? viewPager.getCurrentItem() : activeCategory;

        if (hasMangasInDefaultCategory) {
            setCategoriesWithDefault(categories);
        } else {
            setCategories(categories);
        }
        // Restore active category
        viewPager.setCurrentItem(activeCat, false);
        if (tabs.getTabCount() > 0) {
            TabLayout.Tab tab = tabs.getTabAt(viewPager.getCurrentItem());
            if (tab != null) tab.select();
        }

        // Send the mangas to child fragments after the adapter is updated
        EventBus.getDefault().postSticky(new LibraryMangasEvent(mangas));
    }

    private void setCategoriesWithDefault(List<Category> categories) {
        List<Category> categoriesWithDefault = new ArrayList<>();
        categoriesWithDefault.add(Category.createDefault());
        categoriesWithDefault.addAll(categories);

        setCategories(categoriesWithDefault);
    }

    private void setCategories(List<Category> categories) {
        adapter.setCategories(categories);
        tabs.setTabsFromPagerAdapter(adapter);
        tabs.setVisibility(categories.size() <= 1 ? View.GONE : View.VISIBLE);
    }

    public void setContextTitle(int count) {
        actionMode.setTitle(getString(R.string.label_selected, count));
    }

    public void setVisibilityOfCoverEdit(int count) {
        // If count = 1 display edit button
        actionMode.getMenu().findItem(R.id.action_edit_cover).setVisible((count == 1));
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
            case R.id.action_edit_cover:
                changeSelectedCover(getPresenter().selectedMangas);
                rebuildAdapter();
                destroyActionModeIfNeeded();
                return true;
            case R.id.action_move_to_category:
                moveMangasToCategories(getPresenter().selectedMangas);
                return true;
            case R.id.action_delete:
                getPresenter().deleteMangas();
                destroyActionModeIfNeeded();
                return true;
        }
        return false;
    }

    /**
     * TODO workaround. Covers won't refresh any other way.
     */
    public void rebuildAdapter() {
        adapter = new LibraryAdapter(getChildFragmentManager());
        viewPager.setAdapter(adapter);
        tabs.setupWithViewPager(viewPager);
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

    private void changeSelectedCover(List<Manga> mangas) {
        if (mangas.size() == 1) {
            selectedCoverManga = mangas.get(0);
            if (selectedCoverManga.favorite) {

                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,
                        getString(R.string.file_select_cover)), REQUEST_IMAGE_OPEN);
            } else {
                ToastUtil.showShort(getContext(), R.string.notification_first_add_to_library);
            }

        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case (REQUEST_IMAGE_OPEN):
                    if (selectedCoverManga != null) {
                        // Get the file's content URI from the incoming Intent
                        Uri selectedImageUri = data.getData();

                        // Convert to absolute path to prevent FileNotFoundException
                        String result = IOHandler.getFilePath(selectedImageUri,
                                getContext().getContentResolver(), getContext());

                        // Get file from filepath
                        File picture = new File(result != null ? result : "");

                        try {
                            // Update cover to selected file, show error if something went wrong
                            if (!getPresenter().editCoverWithLocalFile(picture, selectedCoverManga))
                                ToastUtil.showShort(getContext(), R.string.notification_manga_update_failed);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
            }
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
            actionMode = getBaseActivity().startSupportActionMode(this);
        }
    }

    public void invalidateActionMode() {
        actionMode.invalidate();
    }

}
