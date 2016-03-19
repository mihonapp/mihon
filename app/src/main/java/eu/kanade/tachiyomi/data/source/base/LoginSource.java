package eu.kanade.tachiyomi.data.source.base;

import android.content.Context;

public abstract class LoginSource extends Source {

    public LoginSource(Context context) {
        super(context);
    }

    @Override
    public boolean isLoginRequired() {
        return true;
    }
}
