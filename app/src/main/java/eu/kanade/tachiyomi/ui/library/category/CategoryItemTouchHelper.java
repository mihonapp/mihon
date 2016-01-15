package eu.kanade.tachiyomi.ui.library.category;

import eu.kanade.tachiyomi.ui.base.adapter.ItemTouchHelperAdapter;
import eu.kanade.tachiyomi.ui.base.adapter.SimpleItemTouchHelperCallback;

public class CategoryItemTouchHelper extends SimpleItemTouchHelperCallback {

    public CategoryItemTouchHelper(ItemTouchHelperAdapter adapter) {
        super(adapter);
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }
}