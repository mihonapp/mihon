package eu.kanade.tachiyomi.ui.base.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import eu.davidea.flexibleadapter.FlexibleAdapter;

public abstract class FlexibleViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, View.OnLongClickListener {

    private final FlexibleAdapter adapter;
    private final OnListItemClickListener onListItemClickListener;

    public FlexibleViewHolder(View itemView,FlexibleAdapter adapter,
                              OnListItemClickListener onListItemClickListener) {
        super(itemView);
        this.adapter = adapter;

        this.onListItemClickListener = onListItemClickListener;

        this.itemView.setOnClickListener(this);
        this.itemView.setOnLongClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (onListItemClickListener.onListItemClick(getAdapterPosition())) {
            toggleActivation();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        onListItemClickListener.onListItemLongClick(getAdapterPosition());
        toggleActivation();
        return true;
    }

    protected void toggleActivation() {
        itemView.setActivated(adapter.isSelected(getAdapterPosition()));
    }

    public interface OnListItemClickListener {
        boolean onListItemClick(int position);
        void onListItemLongClick(int position);
    }

}