package eu.kanade.mangafeed.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.models.Manga;
import uk.co.ribot.easyadapter.annotations.LayoutId;

/**
 * Created by len on 25/09/2015.
 */

@LayoutId(R.layout.item_library)
public class LibraryAdapter extends ArrayAdapter<Manga> {

    Context context;
    int layoutResourceId;
    ArrayList<Manga> data;

    public LibraryAdapter(Context context, int layoutResourceId, ArrayList<Manga> data) {
        super(context, layoutResourceId, data);
        this.context = context;
        this.layoutResourceId = layoutResourceId;
        this.data = data;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        MangoHolder holder = null;

        if(row == null) {
            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);

            holder = new MangoHolder(row);
            row.setTag(holder);
        }
        else {
            holder = (MangoHolder)row.getTag();
        }

        Manga m = data.get(position);
        holder.nameText.setText(m.title);
        Glide.with(getContext())
                .load(getImageUrl())
                .centerCrop()
                .into(holder.thumbnail);

        return row;
    }

    public void setData(ArrayList<Manga> mangas) {
        // Avoid calling dataSetChanged twice
        data.clear();
        addAll(mangas);
    }

    private String getImageUrl() {
        return "http://img1.wikia.nocookie.net/__cb20090524204255/starwars/images/thumb/1/1a/R2d2.jpg/400px-R2d2.jpg";
    }

    static class MangoHolder {
        @Bind(R.id.thumbnailImageView)
        ImageView thumbnail;

        @Bind(R.id.nameTextView)
        TextView nameText;

        public MangoHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }


}
