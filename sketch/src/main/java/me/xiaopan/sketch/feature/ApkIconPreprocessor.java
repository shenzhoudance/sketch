/*
 * Copyright (C) 2017 Peng fei Pan <sky@xiaopan.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch.feature;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

import me.xiaopan.sketch.SLog;
import me.xiaopan.sketch.SLogType;
import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.cache.BitmapPool;
import me.xiaopan.sketch.cache.BitmapPoolUtils;
import me.xiaopan.sketch.cache.DiskCache;
import me.xiaopan.sketch.request.ImageFrom;
import me.xiaopan.sketch.request.LoadOptions;
import me.xiaopan.sketch.request.UriScheme;
import me.xiaopan.sketch.util.DiskLruCache;
import me.xiaopan.sketch.util.SketchUtils;

public class ApkIconPreprocessor implements ImagePreprocessor.Preprocessor {

    private static final String LOG_NAME = "ApkIconPreprocessor";

    @Override
    public boolean match(Context context, String imageUri, UriScheme uriScheme, String uriContent, LoadOptions options) {
        return uriScheme == UriScheme.FILE && SketchUtils.checkSuffix(uriContent, ".apk");
    }

    @Override
    public PreProcessResult process(Context context, String imageUri, UriScheme uriScheme, String uriContent, LoadOptions options) {
        File apkFile = new File(uriContent);
        if (!apkFile.exists()) {
            return null;
        }
        long lastModifyTime = apkFile.lastModified();
        String diskCacheKey = uriContent + "." + lastModifyTime;

        DiskCache diskCache = Sketch.with(context).getConfiguration().getDiskCache();

        DiskCache.Entry cacheEntry = diskCache.get(diskCacheKey);
        if (cacheEntry != null) {
            return new PreProcessResult(cacheEntry, ImageFrom.DISK_CACHE);
        }

        ReentrantLock diskCacheEditLock = diskCache.getEditLock(diskCacheKey);
        diskCacheEditLock.lock();

        PreProcessResult result = readApkIcon(context, imageUri, uriContent, options, diskCache, diskCacheKey);

        diskCacheEditLock.unlock();
        return result;
    }

    private PreProcessResult readApkIcon(Context context, String imageUri,
                                         String uriContent, LoadOptions options, DiskCache diskCache, String diskCacheKey) {
        BitmapPool bitmapPool = Sketch.with(context).getConfiguration().getBitmapPool();
        boolean lowQualityImage = options != null && options.isLowQualityImage();
        Bitmap iconBitmap = SketchUtils.readApkIcon(context, uriContent, lowQualityImage, LOG_NAME, bitmapPool);
        if (iconBitmap == null) {
            return null;
        }
        if (iconBitmap.isRecycled()) {
            if (SLogType.REQUEST.isEnabled()) {
                SLog.w(SLogType.REQUEST, LOG_NAME, "apk icon bitmap recycled. %s", imageUri);
            }
            return null;
        }

        DiskCache.Editor diskCacheEditor = diskCache.edit(diskCacheKey);
        OutputStream outputStream;
        if (diskCacheEditor != null) {
            try {
                outputStream = new BufferedOutputStream(diskCacheEditor.newOutputStream(), 8 * 1024);
            } catch (IOException e) {
                e.printStackTrace();
                BitmapPoolUtils.freeBitmapToPool(iconBitmap, bitmapPool);
                diskCacheEditor.abort();
                return null;
            }
        } else {
            outputStream = new ByteArrayOutputStream();
        }

        try {
            iconBitmap.compress(SketchUtils.bitmapConfigToCompressFormat(iconBitmap.getConfig()), 100, outputStream);

            if (diskCacheEditor != null) {
                diskCacheEditor.commit();
            }
        } catch (DiskLruCache.EditorChangedException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } catch (DiskLruCache.ClosedException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } catch (DiskLruCache.FileNotExistException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } finally {
            BitmapPoolUtils.freeBitmapToPool(iconBitmap, bitmapPool);
            SketchUtils.close(outputStream);
        }

        if (diskCacheEditor != null) {
            DiskCache.Entry cacheEntry = diskCache.get(diskCacheKey);
            if (cacheEntry != null) {
                return new PreProcessResult(cacheEntry, ImageFrom.LOCAL);
            } else {
                if (SLogType.REQUEST.isEnabled()) {
                    SLog.w(SLogType.REQUEST, LOG_NAME, "not found apk icon cache file. %s", imageUri);
                }
                return null;
            }
        } else {
            return new PreProcessResult(((ByteArrayOutputStream) outputStream).toByteArray(), ImageFrom.LOCAL);
        }
    }
}