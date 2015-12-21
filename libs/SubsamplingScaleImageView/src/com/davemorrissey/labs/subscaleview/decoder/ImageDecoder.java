package com.davemorrissey.labs.subscaleview.decoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

/**
 * Interface for image decoding classes, allowing the default {@link android.graphics.BitmapRegionDecoder}
 * based on the Skia library to be replaced with a custom class.
 */
public interface ImageDecoder {

    /**
     * Decode an image. When possible, initial setup work once in this method. This method
     * must return the dimensions of the image. The URI can be in one of the following formats:
     * File: file:///scard/picture.jpg
     * Asset: file:///android_asset/picture.png
     * Resource: android.resource://com.example.app/drawable/picture
     * @param context Application context. A reference may be held, but must be cleared on recycle.
     * @param uri URI of the image.
     * @return Dimensions of the image.
     * @throws Exception if initialisation fails.
     */
    Bitmap decode(Context context, Uri uri) throws Exception;

}
