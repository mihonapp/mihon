package eu.kanade.tachiyomi.ui.reader.viewer.base;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.source.model.Page;
import eu.kanade.tachiyomi.ui.reader.ReaderActivity;
import rx.functions.Action0;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class PageDecodeErrorLayout extends LinearLayout {

    private final int lightGreyColor;
    private final int blackColor;

    public PageDecodeErrorLayout(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER);

        lightGreyColor = ContextCompat.getColor(context, R.color.light_grey);
        blackColor = ContextCompat.getColor(context, R.color.primary_text);
    }

    public PageDecodeErrorLayout(Context context, Page page, int theme, Action0 retryListener) {
        this(context);

        TextView errorText = new TextView(context);
        errorText.setGravity(Gravity.CENTER);
        errorText.setText(R.string.decode_image_error);
        errorText.setTextColor(theme == ReaderActivity.BLACK_THEME ? lightGreyColor : blackColor);

        Button retryButton = new Button(context);
        retryButton.setLayoutParams(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        retryButton.setText(R.string.action_retry);
        retryButton.setOnClickListener((v) -> {
            removeAllViews();
            retryListener.call();
        });

        Button openInBrowserButton = new Button(context);
        openInBrowserButton.setLayoutParams(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        openInBrowserButton.setText(R.string.action_open_in_browser);
        openInBrowserButton.setOnClickListener((v) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(page.getImageUrl()));
            context.startActivity(intent);
        });

        if (page.getImageUrl() == null) {
            openInBrowserButton.setVisibility(View.GONE);
        }

        addView(errorText);
        addView(retryButton);
        addView(openInBrowserButton);
    }
}
