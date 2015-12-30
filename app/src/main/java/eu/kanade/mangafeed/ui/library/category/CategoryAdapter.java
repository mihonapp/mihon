package eu.kanade.mangafeed.ui.library.category;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.amulyakhare.textdrawable.util.ColorGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;
import eu.kanade.mangafeed.ui.base.adapter.ItemTouchHelperAdapter;

public class CategoryAdapter extends FlexibleAdapter<CategoryHolder, Category> implements
        ItemTouchHelperAdapter {

    private final CategoryActivity activity;
    private final ColorGenerator generator;

    public CategoryAdapter(CategoryActivity activity) {
        this.activity = activity;
        generator = ColorGenerator.DEFAULT;
        setHasStableIds(true);
    }

    public void setItems(List<Category> items) {
        mItems = new ArrayList<>(items);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).id;
    }

    @Override
    public void updateDataSet(String param) {
        
    }

    @Override
    public CategoryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = activity.getLayoutInflater();
        View v = inflater.inflate(R.layout.item_edit_categories, parent, false);
        return new CategoryHolder(v, this, activity, activity);
    }

    @Override
    public void onBindViewHolder(CategoryHolder holder, int position) {
        final Category category = getItem(position);
        holder.onSetValues(category, generator);

        //When user scrolls this bind the correct selection status
        holder.itemView.setActivated(isSelected(position));
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(mItems, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(mItems, i, i - 1);
            }
        }

        activity.getPresenter().reorderCategories(mItems);
    }

    @Override
    public void onItemDismiss(int position) {

    }
}
