package com.davemorrissey.labs.subscaleview.decoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import rapid.decoder.BitmapDecoder;

/**
 * A very simple implementation of {@link com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder}
 * using the RapidDecoder library (https://github.com/suckgamony/RapidDecoder). For PNGs, this can
 * give more reliable decoding and better performance. For JPGs, it is slower and can run out of
 * memory with large images, but has better support for grayscale and CMYK images.
 *
 * This is an incomplete and untested implementation provided as an example only.
 */
public class RapidImageDecoder implements ImageDecoder {

    @Override
    public Bitmap decode(Context context, Uri uri) throws Exception {
        return BitmapDecoder.from(context, uri).useBuiltInDecoder(true).config(Bitmap.Config.RGB_565).decode();
    }

}