package eu.kanade.mangafeed.ui.catalogue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.database.models.Manga;

public class CatalogueAdapter extends ArrayAdapter<Manga> {

    private CatalogueFragment fragment;
    private LayoutInflater inflater;

    public CatalogueAdapter(CatalogueFragment fragment) {
        super(fragment.getActivity(), 0, new ArrayList<>());
        this.fragment = fragment;
        inflater = fragment.getActivity().getLayoutInflater();
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        Manga manga = getItem(position);

        ViewHolder holder;
        if (view != null) {
            holder = (ViewHolder) view.getTag();
        } else {
            view = inflater.inflate(R.layout.item_catalogue, parent, false);
            holder = new ViewHolder(view, fragment);
            view.setTag(holder);
        }
        holder.onSetValues(manga);
        return view;
    }

    static class ViewHolder {
        @Bind(R.id.title) TextView title;
        @Bind(R.id.author) TextView author;
        @Bind(R.id.thumbnail) ImageView thumbnail;
        @Bind(R.id.favorite_sticker) ImageView favorite_sticker;

        CataloguePresenter presenter;

        public ViewHolder(View view, CatalogueFragment fragment) {
            ButterKnife.bind(this, view);
            presenter = fragment.getPresenter();
        }

        public void onSetValues(Manga manga) {
            title.setText(manga.title);
            author.setText(manga.author);

            if (manga.thumbnail_url != null) {
                presenter.coverCache.loadFromCacheOrNetwork(thumbnail, manga.thumbnail_url,
                        presenter.getSource().getGlideHeaders());
            } else {
                thumbnail.setImageResource(android.R.color.transparent);
            }

            if (manga.favorite) {
                favorite_sticker.setVisibility(View.VISIBLE);
            } else {
                favorite_sticker.setVisibility(View.INVISIBLE);
            }
        }
    }
}
