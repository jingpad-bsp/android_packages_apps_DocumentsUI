/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;

import com.android.documentsui.base.MimeTypes;

public class IconUtils {
    public static Drawable loadPackageIcon(Context context, String authority, int icon) {
        if (icon != 0) {
            if (authority != null) {
                final PackageManager pm = context.getPackageManager();
                final ProviderInfo info = pm.resolveContentProvider(authority, 0);
                if (info != null) {
                    return pm.getDrawable(info.packageName, icon, info.applicationInfo);
                }
            } else {
                return context.getDrawable(icon);
            }
        }
        return null;
    }

    public static Drawable loadMimeIcon(
            Context context, String mimeType, String authority, String docId, int mode) {
        return loadMimeIcon(context, mimeType);
    }

    /**
     * Load mime type drawable from system MimeIconUtils.
     * @param context activity context to obtain resource
     * @param mimeType specific mime type string of file
     * @return drawable of mime type files from system default
     */
    public static Drawable loadMimeIcon(Context context, String mimeType) {
        if (mimeType == null) return null;
        Log.e("jake2","load mine icon = " + mimeType);
        switch (mimeType) {
            case "image/*":
                return context.getDrawable(R.drawable.ic_root_image_n);
            case "image/jpeg":
            case "image/png":
            case "image/jpg":
            case "image/gif":
            case "image/bmp":
                return context.getDrawable(R.drawable.ic_mime_image);
            case "video/*":
                return context.getDrawable(R.drawable.ic_root_video_n);
            case "video/mp4":
            case "video/avi":
            case "video/mov":
            case "video/rmvb":
                return context.getDrawable(R.drawable.ic_mime_video);
            case "audio/*":
                return context.getDrawable(R.drawable.ic_root_audio_n);
            case "audio/mp3":
            case "audio/mpeg":
                return context.getDrawable(R.drawable.ic_mime_audio);
            case "application/vnd.android.package-archive": // apk
                return context.getDrawable(R.drawable.ic_mime_apk);
            case "application/json":
                return context.getDrawable(R.drawable.ic_mime_json);
            case "text/plain":
                return context.getDrawable(R.drawable.ic_mime_text);
            case "text/html": // xsl
            case "text/xml":
                return context.getDrawable(R.drawable.ic_mime_html);
            case "application/octet-stream":
                return context.getDrawable(R.drawable.ic_mime_dat);
            case "application/zip":
            return context.getDrawable(R.drawable.ic_mime_zip);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": // word
                return context.getDrawable(R.drawable.ic_mime_wd);
            case "application/pdf": // pdf
                return context.getDrawable(R.drawable.ic_mime_pdf);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": // xsl
                return context.getDrawable(R.drawable.ic_mime_xsl);
            case "application/vnd.ms-powerpoint": // ppt
                return context.getDrawable(R.drawable.ic_mime_ppt);

            case "vnd.android.document/directory":
                return context.getDrawable(R.drawable.ic_mime_directory);
            case "multipart/related":
                return context.getDrawable(R.drawable.ic_mime_unknown);
        }
        return context.getContentResolver().getTypeInfo(mimeType).getIcon().loadDrawable(context);
    }

    public static Drawable applyTintColor(Context context, int drawableId, int tintColorId) {
        final Drawable icon = context.getDrawable(drawableId);
        return applyTintColor(context, icon, tintColorId);
    }

    public static Drawable applyTintColor(Context context, Drawable icon, int tintColorId) {
//        icon.mutate();
//        icon.setTintList(context.getColorStateList(tintColorId));
        return icon;
    }

    public static Drawable applyTintAttr(Context context, int drawableId, int tintAttrId) {
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(tintAttrId, outValue, true);
        return applyTintColor(context, drawableId, outValue.resourceId);
    }
}
