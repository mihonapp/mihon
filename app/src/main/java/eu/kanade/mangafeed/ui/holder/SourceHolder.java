package eu.kanade.mangafeed.ui.holder;

import android.view.View;
import android.widget.TextView;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.sources.base.Source;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;


@LayoutId(R.layout.item_source)
public class SourceHolder extends ItemViewHolder<Source> {

    @ViewId(R.id.source_name)
    TextView source_name;

    public SourceHolder(View view) {
        super(view);
    }

    @Override
    public void onSetValues(Source item, PositionInfo positionInfo) {
        source_name.setText(item.getName());
    }
}
