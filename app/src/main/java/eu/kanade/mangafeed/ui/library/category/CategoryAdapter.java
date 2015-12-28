package eu.kanade.mangafeed.ui.library.category;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.amulyakhare.textdrawable.util.ColorGenerator;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Category;

public class CategoryAdapter extends FlexibleAdapter<CategoryHolder, Category> {
    
    private CategoryFragment fragment;
    private ColorGenerator generator;

    public CategoryAdapter(CategoryFragment fragment) {
        this.fragment = fragment;
        setHasStableIds(true);
        generator = ColorGenerator.DEFAULT;
    }

    public void setItems(List<Category> items) {
        mItems = items;
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
        LayoutInflater inflater = LayoutInflater.from(fragment.getActivity());
        View v = inflater.inflate(R.layout.item_edit_categories, parent, false);
        return new CategoryHolder(v, this, fragment);
    }

    @Override
    public void onBindViewHolder(CategoryHolder holder, int position) {
        final Category category = getItem(position);
        holder.onSetValues(category, generator);

        //When user scrolls this bind the correct selection status
        holder.itemView.setActivated(isSelected(position));
    }

    public ColorGenerator getColorGenerator() {
        return generator;
    }
}
