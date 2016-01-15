package eu.kanade.tachiyomi.widget.preference;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;

public class LibraryColumnsDialog extends DialogPreference {

    private Context context;
    private PreferencesHelper preferences;

    @Bind(R.id.portrait_columns) NumberPicker portraitColumns;
    @Bind(R.id.landscape_columns) NumberPicker landscapeColumns;

    public LibraryColumnsDialog(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LibraryColumnsDialog(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        this.context = context;
        setDialogLayoutResource(R.layout.pref_library_columns);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        ButterKnife.bind(this, view);

        portraitColumns.setValue(preferences.portraitColumns().get());
        landscapeColumns.setValue(preferences.landscapeColumns().get());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            preferences.portraitColumns().set(portraitColumns.getValue());
            preferences.landscapeColumns().set(landscapeColumns.getValue());
            updateSummary();
        }
    }

    private void updateSummary() {
        setSummary(getColumnsSummary());
    }

    private String getColumnsSummary() {
        return String.format("%s: %s, %s: %s",
                context.getString(R.string.portrait),
                getColumnValue(preferences.portraitColumns().get()),
                context.getString(R.string.landscape),
                getColumnValue(preferences.landscapeColumns().get()));
    }

    private String getColumnValue(int value) {
        return value == 0 ? context.getString(R.string.default_columns) : value + "";
    }

    public void setPreferencesHelper(PreferencesHelper preferences) {
        this.preferences = preferences;

        // Set initial summary when the preferences helper is provided
        updateSummary();
    }

}
