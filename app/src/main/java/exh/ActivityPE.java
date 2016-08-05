package exh;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Map;

import eu.kanade.tachiyomi.R;

public class ActivityPE extends Activity {

    ListView listView;
    ArrayAdapter listAdapter;
    SharedPreferences preferences;

    void updateList() {
        listAdapter.clear();
        listAdapter.addAll(preferences.getAll().entrySet());
        listView.deferNotifyDataSetChanged();
        listView.invalidate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityPE instance = this;

        setContentView(R.layout.activity_pe);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        listView = (ListView) findViewById(R.id.peList);
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList(preferences.getAll().entrySet()));
        listView.setAdapter(listAdapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final Map.Entry<String, ?> entry = (Map.Entry<String, ?>) listAdapter.getItem(position);
                new AlertDialog.Builder(instance)
                        .setTitle("Delete Preference Entry")
                        .setMessage("Delete '" + entry.getKey() + "'?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                preferences.edit().remove(entry.getKey()).commit();
                                ActivityPE.this.updateList();
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
                return true;
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Map.Entry<String, ?> entry = (Map.Entry<String, ?>) listAdapter.getItem(position);
                if (entry != null) {
                    LinearLayout view1 = new LinearLayout(instance);
                    view1.setOrientation(LinearLayout.VERTICAL);
                    EditText keyView = new EditText(instance);
                    keyView.setHint("Key");
                    keyView.setText(entry.getKey());
                    keyView.setEnabled(false);
                    final EditText valueView = new EditText(instance);
                    valueView.setHint("Value");
                    valueView.setText(entry.getValue().toString());
                    view1.addView(keyView);
                    view1.addView(valueView);
                    new AlertDialog.Builder(instance)
                            .setTitle("Edit Entry")
                            .setView(view1)
                            .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int which) {
                                    Object object = entry.getValue();
                                    String key = entry.getKey();
                                    String value = valueView.getText().toString();
                                    SharedPreferences.Editor editor = preferences.edit();
                                    try {
                                        if (object instanceof Boolean) {
                                            editor.putBoolean(key, Boolean.parseBoolean(value));
                                        } else if (object instanceof Integer) {
                                            editor.putInt(key, Integer.parseInt(value));
                                        } else if (object instanceof String) {
                                            editor.putString(key, value);
                                        } else if (object instanceof Float) {
                                            editor.putFloat(key, Float.parseFloat(value));
                                        } else if (object instanceof Long) {
                                            editor.putLong(key, Long.parseLong(value));
                                        } else {
                                            new AlertDialog.Builder(instance)
                                                    .setTitle("Error")
                                                    .setMessage("Unsupported type!")
                                                    .show();
                                        }
                                    } catch (Exception e) {
                                        new AlertDialog.Builder(instance)
                                                .setTitle("Error")
                                                .setMessage("Type mismatch!")
                                                .show();
                                    }
                                    editor.commit();
                                    ActivityPE.this.updateList();
                                    dialog.dismiss();
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .show();
                }
            }
        });
    }
}