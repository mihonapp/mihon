/*
 * Copyright (C) 2014 Ribot Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.kanade.mangafeed.ui.adapter;

import android.content.Context;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.models.Manga;
import uk.co.ribot.easyadapter.EasyAdapter;
import uk.co.ribot.easyadapter.ItemViewHolder;

public class CatalogueArrayAdapter<T> extends EasyAdapter<T> implements Filterable {

    List<Manga> mangas;

    public CatalogueArrayAdapter(Context context, Class<? extends ItemViewHolder> itemViewHolderClass, List<T> listItems) {
        super(context, itemViewHolderClass, listItems);
        mangas = (List<Manga>)getItems();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                FilterResults results = new FilterResults();
                String query = charSequence.toString().toLowerCase();

                if (query == null || query.length() == 0) {
                    results.values = mangas;
                    results.count = mangas.size();
                } else {
                    ArrayList<Manga> filterResultsData = new ArrayList<>();
                    for (Manga manga: mangas) {
                        if (manga.title.toLowerCase().contains(query) ||
                                manga.author.toLowerCase().contains(query) ||
                                manga.artist.toLowerCase().contains(query)) {
                            filterResultsData.add(manga);
                        }
                    }
                    results.values = filterResultsData;
                    results.count = filterResultsData.size();
                }
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                setItems((List<T>) results.values);
            }
        };
    }
}
