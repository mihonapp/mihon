package exh;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.pushtorefresh.storio.sqlite.operations.put.PutResult;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.source.online.english.EHentai;
import rx.functions.Action1;

public class ActivityBatchAdd extends AppCompatActivity {

    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_batch_add);

        //Inject later (but I don't know how to use this dep-injection library)
        db = new DatabaseHelper(this);

        findViewById(R.id.addButton).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final EditText textBox = ((EditText) ActivityBatchAdd.this.findViewById(R.id.galleryList));
                String textBoxContent = textBox.getText().toString();
                if (textBoxContent.isEmpty()) {
                    new AlertDialog.Builder(ActivityBatchAdd.this)
                            .setTitle("No galleries to add!")
                            .setMessage("You must specify at least one gallery to add!")
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .show();
                    return;
                }
                final ProgressDialog progressDialog
                        = ProgressDialog.show(ActivityBatchAdd.this, "Adding galleries...", "Initializing...", false, false);
                final StringJoiner report = new StringJoiner("\n");
                final String[] splitUrls = textBoxContent.split("\n");
                final ArrayList<String> failed = new ArrayList<>();
                progressDialog.setMax(splitUrls.length);
                new Thread(new Runnable() {
                    @Override public void run() {
                        for (int i = 0; i < splitUrls.length; i++) {
                            final String trimmed = splitUrls[i].trim();
                            final int finalI = i;
                            ActivityBatchAdd.this.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    progressDialog.setMessage("Adding: '" + trimmed + "'... (" + (finalI + 1) + "/" + splitUrls.length + ")");
                                    progressDialog.setProgress(finalI);
                                }
                            });
                            try {
                                if (TextUtils.isEmpty(trimmed)) {
                                    throw new MalformedURLException("Empty URL!");
                                }
                                URL parsedUrl = new URL(trimmed);
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
                                final Manga manga = Manga.Companion.create(EHentai.pathOnly(trimmed), source);
                                manga.setTitle(trimmed);
                                manga.setFavorite(true);
                                db.insertManga(manga).asRxObservable().single().forEach(new Action1<PutResult>() {
                                    @Override public void call(PutResult putResult) {
                                        manga.setId(putResult.insertedId());
                                    }
                                });
                                report.add("Successfully added: " + trimmed);
                            } catch (MalformedURLException e) {
                                Log.e("EHentai", "Could not add URL: " + trimmed + "!", e);
                                report.add("Coult not add: " + trimmed);
                                failed.add(trimmed);
                            }
                        }
                        if (failed.size() > 0) {
                            report.add("Failed to add " + failed.size() + " galleries!");
                        }
                        ActivityBatchAdd.this.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (failed.size() > 0) {
                                    StringJoiner failedJoiner = new StringJoiner("\n");
                                    for (String failedUrl : failed)
                                        failedJoiner.add(failedUrl);
                                    textBox.setText(failedJoiner.toString());
                                } else {
                                    textBox.setText("");
                                }
                                new AlertDialog.Builder(ActivityBatchAdd.this)
                                        .setTitle("Batch Add Report")
                                        .setMessage(report.toString())
                                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog1, int which) {
                                                dialog1.dismiss();
                                            }
                                        })
                                        .show();
                            }
                        });
                        progressDialog.dismiss();
                    }
                }).start();
            }
        });
    }
}
