package eu.kanade.mangafeed.ui.library.category;

import eu.kanade.mangafeed.ui.base.adapter.ItemTouchHelperAdapter;
import eu.kanade.mangafeed.ui.base.adapter.SimpleItemTouchHelperCallback;

public class CategoryItemTouchHelper extends SimpleItemTouchHelperCallback {

    public CategoryItemTouchHelper(ItemTouchHelperAdapter adapter) {
        super(adapter);
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }
}