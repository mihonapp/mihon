package eu.kanade.mangafeed.ui.library.category;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.ui.base.activity.BaseRxActivity;
import eu.kanade.mangafeed.ui.base.adapter.FlexibleViewHolder;
import eu.kanade.mangafeed.ui.base.adapter.OnStartDragListener;
import eu.kanade.mangafeed.ui.decoration.DividerItemDecoration;
import eu.kanade.mangafeed.ui.library.LibraryCategoryAdapter;
import nucleus.factory.RequiresPresenter;
import rx.Observable;

@RequiresPresenter(CategoryPresenter.class)
public class CategoryActivity extends BaseRxActivity<CategoryPresenter> implements
        ActionMode.Callback, FlexibleViewHolder.OnListItemClickListener, OnStartDragListener {

    @Bind(R.id.toolbar) Toolbar toolbar;
    @Bind(R.id.categories_list) RecyclerView recycler;
    @Bind(R.id.fab) FloatingActionButton fab;

    private CategoryAdapter adapter;
    private ActionMode actionMode;
    private ItemTouchHelper touchHelper;

    public static Intent newIntent(Context context) {
        return new Intent(context, CategoryActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_edit_categories);
        ButterKnife.bind(this);

        setupToolbar(toolbar);

        adapter = new CategoryAdapter(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setAdapter(adapter);
        recycler.addItemDecoration(new DividerItemDecoration(
                ResourcesCompat.getDrawable(getResources(), R.drawable.line_divider, null)));

        // Touch helper to drag and reorder categories
        touchHelper = new ItemTouchHelper(new CategoryItemTouchHelper(adapter));
        touchHelper.attachToRecyclerView(recycler);

        fab.setOnClickListener(v -> {
            new MaterialDialog.Builder(this)
                    .title(R.string.action_add_category)
                    .input(R.string.name, 0, false, (dialog, input) -> {
                        getPresenter().createCategory(input.toString());
                    })
                    .show();
        });
    }

    public void setCategories(List<Category> categories) {
        destroyActionModeIfNeeded();
        adapter.setItems(categories);
    }

    private List<Category> getSelectedCategories() {
        // Create a blocking copy of the selected categories
        return Observable.from(adapter.getSelectedItems())
                .map(adapter::getItem).toList().toBlocking().single();
    }

    @Override
    public boolean onListItemClick(int position) {
        if (actionMode != null && position != -1) {
            toggleSelection(position);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onListItemLongClick(int position) {
        if (actionMode == null)
            actionMode = startSupportActionMode(this);

        toggleSelection(position);
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position, false);

        int count = adapter.getSelectedItemCount();
        if (count == 0) {
            actionMode.finish();
        } else {
            setContextTitle(count);
            actionMode.invalidate();
            MenuItem editItem = actionMode.getMenu().findItem(R.id.action_edit);
            editItem.setVisible(count == 1);
        }
    }

    private void setContextTitle(int count) {
        actionMode.setTitle(getString(R.string.label_selected, count));
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.category_selection, menu);
        adapter.setMode(LibraryCategoryAdapter.MODE_MULTI);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                deleteCategories(getSelectedCategories());
                return true;
            case R.id.action_edit:
                editCategory(getSelectedCategories().get(0));
                return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        adapter.setMode(LibraryCategoryAdapter.MODE_SINGLE);
        adapter.clearSelection();
        actionMode = null;
    }

    public void destroyActionModeIfNeeded() {
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private void deleteCategories(List<Category> categories) {
        getPresenter().deleteCategories(categories);
    }

    private void editCategory(Category category) {
        new MaterialDialog.Builder(this)
                .title(R.string.action_rename_category)
                .input(getString(R.string.name), category.name, false, (dialog, input) -> {
                    getPresenter().renameCategory(category, input.toString());
                })
                .show();
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        touchHelper.startDrag(viewHolder);
    }

}
