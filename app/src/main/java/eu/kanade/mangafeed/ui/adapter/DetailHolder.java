package eu.kanade.mangafeed.ui.adapter;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;

@LayoutId(eu.kanade.mangafeed.R.layout.item_detail)
public class DetailHolder extends ItemViewHolder<String> {

    @ViewId(eu.kanade.mangafeed.R.id.text_detail)
    TextView mDetailText;

    public DetailHolder(View view) {
        super(view);
    }

    @Override
    public void onSetValues(String item, PositionInfo positionInfo) {
        mDetailText.setText(item);
    }

    @Override
    public void onSetListeners() {
        mDetailText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getItem())));
            }
        });
    }
}