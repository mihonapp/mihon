package com.davemorrissey.labs.subscaleview.decoder;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;

import java.io.InputStream;
import java.util.List;

/**
 * Default implementation of {@link com.davemorrissey.labs.subscaleview.decoder.ImageDecoder}
 * using Android's {@link android.graphics.BitmapFactory}, based on the Skia library. This
 * works well in most circumstances and has reasonable performance, however it has some problems
 * with grayscale, indexed and CMYK images.
 */
public class SkiaImageDecoder implements ImageDecoder {

    private static final String FILE_PREFIX = "file://";
    private static final String ASSET_PREFIX = FILE_PREFIX + "/android_asset/";
    private static final String RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://";

    @Override
    public Bitmap decode(Context context, Uri uri) throws Exception {
        String uriString = uri.toString();
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            Resources res;
            String packageName = uri.getAuthority();
            if (context.getPackageName().equals(packageName)) {
                res = context.getResources();
            } else {
                PackageManager pm = context.getPackageManager();
                res = pm.getResourcesForApplication(packageName);
            }

            int id = 0;
            List<String> segments = uri.getPathSegments();
            int size = segments.size();
            if (size == 2 && segments.get(0).equals("drawable")) {
                String resName = segments.get(1);
                id = res.getIdentifier(resName, "drawable", packageName);
            } else if (size == 1 && TextUtils.isDigitsOnly(segments.get(0))) {
                try {
                    id = Integer.parseInt(segments.get(0));
                } catch (NumberFormatException ignored) {
                }
            }

            bitmap = BitmapFactory.decodeResource(context.getResources(), id, options);
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            String assetName = uriString.substring(ASSET_PREFIX.length());
            bitmap = BitmapFactory.decodeStream(context.getAssets().open(assetName), null, options);
        } else if (uriString.startsWith(FILE_PREFIX)) {
            bitmap = BitmapFactory.decodeFile(uriString.substring(FILE_PREFIX.length()), options);
        } else {
            InputStream inputStream = null;
            try {
                ContentResolver contentResolver = context.getContentResolver();
                inputStream = contentResolver.openInputStream(uri);
                bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (Exception e) { }
                }
            }
        }
        if (bitmap == null) {
            throw new RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported");
        }
        return bitmap;
    }
}
