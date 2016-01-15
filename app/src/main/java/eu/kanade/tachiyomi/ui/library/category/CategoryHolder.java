package eu.kanade.tachiyomi.ui.library.category;

import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.Category;
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder;
import eu.kanade.tachiyomi.ui.base.adapter.OnStartDragListener;

public class CategoryHolder extends FlexibleViewHolder {

    private View view;

    @Bind(R.id.image) ImageView image;
    @Bind(R.id.title) TextView title;
    @Bind(R.id.reorder) ImageView reorder;

    public CategoryHolder(View view, CategoryAdapter adapter,
                          OnListItemClickListener listener, OnStartDragListener dragListener) {
        super(view, adapter, listener);
        ButterKnife.bind(this, view);
        this.view = view;

        reorder.setOnTouchListener((v, event) -> {
            if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                dragListener.onStartDrag(this);
                return true;
            }
            return false;
        });
    }

    public void onSetValues(Category category, ColorGenerator generator) {
        title.setText(category.name);
        image.setImageDrawable(getRound(category.name.substring(0, 1), generator));
    }

    private TextDrawable getRound(String text, ColorGenerator generator) {
        return TextDrawable.builder().buildRound(text, generator.getColor(text));
    }

    @OnClick(R.id.image)
    void onImageClick() {
        // Simulate long click on this view to enter selection mode
        onLongClick(view);
    }

}