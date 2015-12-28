package eu.kanade.mangafeed.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.data.sync.LibraryUpdateService;
import eu.kanade.mangafeed.ui.base.fragment.BaseRxFragment;
import eu.kanade.mangafeed.ui.library.category.CategoryFragment;
import eu.kanade.mangafeed.ui.main.MainActivity;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(LibraryPresenter.class)
public class LibraryFragment extends BaseRxFragment<LibraryPresenter> {

    TabLayout tabs;
    AppBarLayout appBar;

    @Bind(R.id.view_pager) ViewPager categoriesPager;
    protected LibraryAdapter adapter;

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

}
