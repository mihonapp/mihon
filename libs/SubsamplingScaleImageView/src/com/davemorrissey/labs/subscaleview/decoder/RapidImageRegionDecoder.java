package com.davemorrissey.labs.subscaleview.decoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
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
public class RapidImageRegionDecoder implements ImageRegionDecoder {

    private BitmapDecoder decoder;

    @Override
    public Point init(Context context, Uri uri) throws Exception {
        decoder = BitmapDecoder.from(context, uri);
        decoder.useBuiltInDecoder(true);
        int width = decoder.sourceWidth();
        int height = decoder.sourceHeight();
        if (width == 0 || height == 0)
            throw new Exception("Rapid image decoder returned empty image - image format may not be supported");
        return new Point(width, height);
    }

    @Override
    public synchronized Bitmap decodeRegion(Rect sRect, int sampleSize) {
        try {
            return decoder.reset().region(sRect).scale(sRect.width() / sampleSize, sRect.height() / sampleSize).decode();
        } catch (Exception e) {
            throw new RuntimeException("Rapid image decoder returned null bitmap - image format may not be supported");
        }
    }

    @Override
    public boolean isReady() {
        return decoder != null;
    }

    @Override
    public void recycle() {
        BitmapDecoder.destroyMemoryCache();
        BitmapDecoder.destroyDiskCache();
        decoder.reset();
        decoder = null;
    }
}