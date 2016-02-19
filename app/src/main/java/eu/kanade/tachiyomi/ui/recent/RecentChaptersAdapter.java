package eu.kanade.tachiyomi.ui.recent;

import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.models.MangaChapter;

/**
 * Adapter of RecentChaptersHolder.
 * Connection between Fragment and Holder
 * Holder updates should be called from here.
 */
public class RecentChaptersAdapter extends FlexibleAdapter<RecyclerView.ViewHolder, Object> {

    /**
     * Fragment of RecentChaptersFragment
     */
    private final RecentChaptersFragment fragment;

    /**
     * The id of the view type
     */
    private static final int VIEW_TYPE_CHAPTER = 0;

    /**
     * The id of the view type
     */
    private static final int VIEW_TYPE_SECTION = 1;

    /**
     * Constructor
     *
     * @param fragment fragment
     */
    public RecentChaptersAdapter(RecentChaptersFragment fragment) {
        this.fragment = fragment;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        Object item = getItem(position);
        if (item instanceof MangaChapter)
            return ((MangaChapter) item).chapter.id;
        else
            return item.hashCode();
    }

    /**
     * Update items
     *
     * @param items items
     */
    public void setItems(List<Object> items) {
        mItems = items;
        notifyDataSetChanged();
    }

    @Override
    public void updateDataSet(String param) {

    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position) instanceof MangaChapter ? VIEW_TYPE_CHAPTER : VIEW_TYPE_SECTION;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v;

        // Check which view type and set correct values.
        switch (viewType) {
            case VIEW_TYPE_CHAPTER:
                v = inflater.inflate(R.layout.item_recent_chapter, parent, false);
                return new RecentChaptersHolder(v, this, fragment);
            case VIEW_TYPE_SECTION:
                v = inflater.inflate(R.layout.item_recent_chapter_section, parent, false);
                return new SectionViewHolder(v);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        // Check which view type and set correct values.
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_CHAPTER:
                final MangaChapter chapter = (MangaChapter) getItem(position);
                ((RecentChaptersHolder) holder).onSetValues(chapter);
                break;
            case VIEW_TYPE_SECTION:
                final Date date = (Date) getItem(position);
                ((SectionViewHolder) holder).onSetValues(date);
                break;
        }

        //When user scrolls this bind the correct selection status
        holder.itemView.setActivated(isSelected(position));
    }

    /**
     * Returns fragment
     * @return RecentChaptersFragment
     */
    public RecentChaptersFragment getFragment() {
        return fragment;
    }

    public static class SectionViewHolder extends RecyclerView.ViewHolder {

        @Bind(R.id.section_text) TextView section;

        private final long now = new Date().getTime();

        public SectionViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        public void onSetValues(Date date) {
            CharSequence s = DateUtils.getRelativeTimeSpanString(
                    date.getTime(), now, DateUtils.DAY_IN_MILLIS);
            section.setText(s);
        }
    }
}
