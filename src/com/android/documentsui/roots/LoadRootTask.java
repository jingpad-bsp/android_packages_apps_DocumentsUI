/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.roots;

import static com.android.documentsui.base.SharedMinimal.DEBUG;

import android.app.Activity;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;

public class LoadRootTask<T extends Activity & CommonAddons>
        extends PairedTask<T, Void, RootInfo> {
    private static final String TAG = "LoadRootTask";

    protected final ProvidersAccess mProviders;

    private final State mState;
    private final Uri mRootUri;

    public LoadRootTask(T activity, ProvidersAccess providers, State state, Uri rootUri) {
        super(activity);
        mState = state;
        mProviders = providers;
        mRootUri = rootUri;
    }

    @Override
    protected RootInfo run(Void... params) {
        if (DEBUG) {
            Log.d(TAG, "Loading root: " + mRootUri);
        }

        return mProviders.getRootOneshot(mRootUri.getAuthority(), getRootId(mRootUri));
    }

    @Override
    protected void finish(RootInfo root) {
        if (root != null) {
            if (DEBUG) {
                Log.d(TAG, "Loaded root: " + root);
            }
            mOwner.onRootPicked(root);
        } else {
            Log.w(TAG, "Failed to find root: " + mRootUri);
            mOwner.finishAndRemoveTask();
        }
    }

    protected String getRootId(Uri rootUri) {
        return DocumentsContract.getRootId(rootUri);
    }
}
