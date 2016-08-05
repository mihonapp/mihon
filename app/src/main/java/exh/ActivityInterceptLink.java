package exh;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.pushtorefresh.storio.sqlite.operations.put.PutResult;

import java.net.MalformedURLException;
import java.net.URL;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.source.online.english.EHentai;
import eu.kanade.tachiyomi.ui.manga.MangaActivity;
import rx.functions.Action1;

public class ActivityInterceptLink extends AppCompatActivity {

    DatabaseHelper db;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Inject later (but I don't know how to use this dep-injection library)
        db = new DatabaseHelper(this);

        setContentView(R.layout.activity_intercept);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        try {
            if (Intent.ACTION_VIEW.equals(action)) {
                String url = intent.getDataString();
                URL parsedUrl = new URL(url);
                int source;
                switch (parsedUrl.getHost()) {
                    case "g.e-hentai.org":
                        source = 1;
                        break;
                    case "exhentai.org":
                        source = 2;
                        break;
                    default:
                        throw new MalformedURLException("Invalid host!");
                }
                final Manga manga = Manga.Companion.create(EHentai.pathOnly(url), source);
                manga.setTitle(url);
                db.insertManga(manga).asRxObservable().single().forEach(new Action1<PutResult>() {
                    @Override public void call(PutResult putResult) {
                        manga.setId(putResult.insertedId());
                        Intent outIntent = MangaActivity.Companion.newIntent(ActivityInterceptLink.this, manga, false);
                        ActivityInterceptLink.this.startActivity(outIntent);
                    }
                });
            } else {
                throw new IllegalArgumentException("Invalid action!");
            }
        } catch (Exception e) {
            Log.e("EHentai", "Error intercepting URL!", e);
            Toast.makeText(this, "Invalid URL!", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
