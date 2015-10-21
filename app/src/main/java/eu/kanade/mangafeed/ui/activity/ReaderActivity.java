package eu.kanade.mangafeed.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.presenter.ReaderPresenter;
import eu.kanade.mangafeed.ui.adapter.ReaderPageAdapter;
import nucleus.factory.RequiresPresenter;

@RequiresPresenter(ReaderPresenter.class)
public class ReaderActivity extends BaseActivity<ReaderPresenter> {

    @Bind(R.id.view_pager) ViewPager viewPager;

    private ReaderPageAdapter adapter;

    public static Intent newInstance(Context context) {
        return new Intent(context, ReaderActivity.class);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_reader);
        ButterKnife.bind(this);

        createAdapter();
        viewPager.setOffscreenPageLimit(3);
    }

    private void createAdapter() {
        adapter = new ReaderPageAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
    }

    public void onPageList(List<Page> pages) {
        adapter.setPages(pages);
    }

    public void onPageDownloaded(Page page) {
        adapter.replacePage(page.getPageNumber(), page);
    }
}
