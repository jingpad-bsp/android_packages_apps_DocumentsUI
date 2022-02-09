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

package com.android.documentsui.base;

import static com.android.documentsui.base.SharedMinimal.TAG;

import android.content.Context;
import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DurableUtils {
    public static <D extends Durable> byte[] writeToArray(D d) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        d.write(new DataOutputStream(out));
        return out.toByteArray();
    }

    public static <D extends Durable> D readFromArray(byte[] data, D d) throws IOException {
        if (data == null) throw new IOException("Missing data");
        final ByteArrayInputStream in = new ByteArrayInputStream(data);
        d.reset();
        try {
            d.read(new DataInputStream(in));
        } catch (IOException e) {
            d.reset();
            throw e;
        }
        return d;
    }

    public static <D extends Durable> byte[] writeToArrayOrNull(D d) {
        try {
            return writeToArray(d);
        } catch (IOException e) {
            Log.w(TAG, "Failed to write", e);
            return null;
        }
    }

    public static <D extends Durable> D readFromArrayOrNull(byte[] data, D d) {
        try {
            return readFromArray(data, d);
        } catch (IOException e) {
            Log.w(TAG, "Failed to read", e);
            return null;
        }
    }

    public static <D extends Durable> void writeToParcel(Parcel parcel, D d) {
        try {
            parcel.writeByteArray(writeToArray(d));
        } catch (IOException e) {
            throw new BadParcelableException(e);
        }
    }

    public static <D extends Durable> D readFromParcel(Parcel parcel, D d) {
        try {
            return readFromArray(parcel.createByteArray(), d);
        } catch (IOException e) {
            throw new BadParcelableException(e);
        }
    }

    public static void writeNullableString(DataOutputStream out, String value) throws IOException {
        if (value != null) {
            out.write(1);
            out.writeUTF(value);
        } else {
            out.write(0);
        }
    }

    public static String readNullableString(DataInputStream in) throws IOException {
        if (in.read() != 0) {
            return in.readUTF();
        } else {
            return null;
        }
    }

    //add by hjy start
    public static void saveParce(Context context, String key, Parcelable parcelable) {
        FileOutputStream fos;
        try {
            fos = context.openFileOutput(key,
                    Context.MODE_PRIVATE);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            Parcel parcel = Parcel.obtain();
            parcel.writeParcelable(parcelable, 0);

            bos.write(parcel.marshall());
            bos.flush();
            bos.close();
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static DocumentInfo loadDocumentInfo(Context context, String key) {
        FileInputStream fis;
        try {
            fis = context.openFileInput(key);
            byte[] bytes = new byte[fis.available()];
            fis.read(bytes);
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            DocumentInfo data = parcel.readParcelable(DocumentInfo.class.getClassLoader());
            fis.close();
            return data;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static RootInfo loadRootInfo(Context context, String key) {
        FileInputStream fis;
        try {
            fis = context.openFileInput(key);
            byte[] bytes = new byte[fis.available()];
            fis.read(bytes);
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            RootInfo data = parcel.readParcelable(RootInfo.class.getClassLoader());
            fis.close();
            return data;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean deleteFavInfo(Context context, String key) {
        File f= new File(context.getFilesDir(), key);
        if(f.exists()){
            f.delete();
            return true;
        }
        return false;
    }
    //add by hjy end
}
