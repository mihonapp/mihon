package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.presenter.ViewerPresenter;
import eu.kanade.mangafeed.ui.adapter.ViewerPageAdapter;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(ViewerPresenter.class)
public class ViewerActivity extends BaseActivity<ViewerPresenter> {

    @Bind(R.id.view_pager) ViewPager viewPager;

    private ViewerPageAdapter adapter;

    public static Intent newInstance(Context context) {
        return new Intent(context, ViewerActivity.class);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_viewer);
        ButterKnife.bind(this);

        createAdapter();
    }

    private void createAdapter() {
        adapter = new ViewerPageAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
    }

}
