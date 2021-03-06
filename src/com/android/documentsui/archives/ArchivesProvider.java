/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.archives;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.graphics.Point;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import com.android.documentsui.R;
import androidx.annotation.GuardedBy;
import android.os.FileUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Provides basic implementation for creating, extracting and accessing
 * files within archives exposed by a document provider.
 *
 * <p>This class is thread safe. All methods can be called on any thread without
 * synchronization.
 */
public class ArchivesProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.android.documentsui.archives";

    private static final String[] DEFAULT_ROOTS_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS,
            Root.COLUMN_ICON };
    private static final String TAG = "ArchivesProvider";
    private static final String METHOD_ACQUIRE_ARCHIVE = "acquireArchive";
    private static final String METHOD_RELEASE_ARCHIVE = "releaseArchive";
    private static final Set<String> ZIP_MIME_TYPES = ArchiveRegistry.getSupportList();


    @GuardedBy("mArchives")
    private final Map<Key, Loader> mArchives = new HashMap<>();

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_ACQUIRE_ARCHIVE.equals(method)) {
            acquireArchive(arg);
            return null;
        }

        if (METHOD_RELEASE_ARCHIVE.equals(method)) {
            releaseArchive(arg);
            return null;
        }

        return super.call(method, arg, extras);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        // add documents root
//        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
//        includeDocsRoot(result);
//        return result;

        // No roots provided.
        return new MatrixCursor(projection != null ? projection : DEFAULT_ROOTS_PROJECTION);
    }

    private static final String TYPE_DOCS_ROOT = "docs_root";
    private static final String TYPE_DOCS_BUCKET = "docs_bucket";
    private static final String TYPE_DOCS = "docs";
    private static final String DOCS_MIME_TYPES = joinNewline("text/*");

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON,
            Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_MIME_TYPES,
            Root.COLUMN_QUERY_ARGS
    };



    private static final String SUPPORTED_QUERY_ARGS = joinNewline(
            DocumentsContract.QUERY_ARG_DISPLAY_NAME,
            DocumentsContract.QUERY_ARG_FILE_SIZE_OVER,
            DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER,
            DocumentsContract.QUERY_ARG_MIME_TYPES);


    private void includeDocsRoot(MatrixCursor result) {
        int flags = Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_RECENTS | Root.FLAG_SUPPORTS_SEARCH;
//        if (isEmpty(Images.Media.EXTERNAL_CONTENT_URI)) {
//            flags |= Root.FLAG_EMPTY;
//            sReturnedImagesEmpty = true;
//        }

        final RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, TYPE_DOCS_ROOT);
        row.add(Root.COLUMN_FLAGS, flags);
        row.add(Root.COLUMN_TITLE, getContext().getString(R.string.chip_title_documents));
        row.add(Root.COLUMN_DOCUMENT_ID, TYPE_DOCS_ROOT);
        row.add(Root.COLUMN_MIME_TYPES, DOCS_MIME_TYPES);
        row.add(Root.COLUMN_QUERY_ARGS, SUPPORTED_QUERY_ARGS);
    }

    private static String joinNewline(String... args) {
        return TextUtils.join("\n", args);
    }

    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private boolean isEmpty(Uri uri) {
        final ContentResolver resolver = getContext().getContentResolver();
        final long token = Binder.clearCallingIdentity();
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[] {
                    BaseColumns._ID }, null, null, null);
            return (cursor == null) || (cursor.getCount() == 0);
        } finally {
//            IoUtils.closeQuietly(cursor);
            cursor.close();
            Binder.restoreCallingIdentity(token);
        }
    }



    @Override
    public Cursor queryChildDocuments(String documentId, @Nullable String[] projection,
            @Nullable String sortOrder)
            throws FileNotFoundException {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        final Loader loader = getLoaderOrThrow(documentId);
        final int status = loader.getStatus();
        // If already loaded, then forward the request to the archive.
        if (status == Loader.STATUS_OPENED) {
            return loader.get().queryChildDocuments(documentId, projection, sortOrder);
        }

        final MatrixCursor cursor = new MatrixCursor(
                projection != null ? projection : Archive.DEFAULT_PROJECTION);
        final Bundle bundle = new Bundle();

        switch (status) {
            case Loader.STATUS_OPENING:
                bundle.putBoolean(DocumentsContract.EXTRA_LOADING, true);
                break;

            case Loader.STATUS_FAILED:
                // Return an empty cursor with EXTRA_LOADING, which shows spinner
                // in DocumentsUI. Once the archive is loaded, the notification will
                // be sent, and the directory reloaded.
                bundle.putString(DocumentsContract.EXTRA_ERROR,
                        getContext().getString(R.string.archive_loading_failed));
                break;
        }

        cursor.setExtras(bundle);
        cursor.setNotificationUri(getContext().getContentResolver(),
                buildUriForArchive(archiveId.mArchiveUri, archiveId.mAccessMode));
        return cursor;
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        if (archiveId.mPath.equals("/")) {
            return Document.MIME_TYPE_DIR;
        }

        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().getDocumentType(documentId);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().isChildDocument(parentDocumentId, documentId);
    }

    @Override
    public @Nullable Bundle getDocumentMetadata(String documentId)
            throws FileNotFoundException {

        final Archive archive = getLoaderOrThrow(documentId).get();
        final String mimeType = archive.getDocumentType(documentId);

        if (!MetadataReader.isSupportedMimeType(mimeType)) {
            return null;
        }

        InputStream stream = null;
        try {
            stream = new ParcelFileDescriptor.AutoCloseInputStream(
                    openDocument(documentId, "r", null));
            final Bundle metadata = new Bundle();
            MetadataReader.getMetadata(metadata, stream, mimeType, null);
            return metadata;
        } catch (IOException e) {
            Log.e(TAG, "An error occurred retrieving the metadata.", e);
            return null;
        } finally {
            FileUtils.closeQuietly(stream);
        }
    }

    @Override
    public Cursor queryDocument(String documentId, @Nullable String[] projection)
            throws FileNotFoundException {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        if (archiveId.mPath.equals("/")) {
            try (final Cursor archiveCursor = getContext().getContentResolver().query(
                    archiveId.mArchiveUri,
                    new String[] { Document.COLUMN_DISPLAY_NAME },
                    null, null, null, null)) {
                if (archiveCursor == null || !archiveCursor.moveToFirst()) {
                    throw new FileNotFoundException(
                            "Cannot resolve display name of the archive.");
                }
                final String displayName = archiveCursor.getString(
                        archiveCursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME));

                final MatrixCursor cursor = new MatrixCursor(
                        projection != null ? projection : Archive.DEFAULT_PROJECTION);
                final RowBuilder row = cursor.newRow();
                row.add(Document.COLUMN_DOCUMENT_ID, documentId);
                row.add(Document.COLUMN_DISPLAY_NAME, displayName);
                row.add(Document.COLUMN_SIZE, 0);
                row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
                return cursor;
            }
        }

        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().queryDocument(documentId, projection);
    }

    @Override
    public String createDocument(
            String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        final Loader loader = getLoaderOrThrow(parentDocumentId);
        return loader.get().createDocument(parentDocumentId, mimeType, displayName);
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId, String mode, final CancellationSignal signal)
            throws FileNotFoundException {
        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().openDocument(documentId, mode, signal);
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(
            String documentId, Point sizeHint, final CancellationSignal signal)
            throws FileNotFoundException {
        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().openDocumentThumbnail(documentId, sizeHint, signal);
    }

    /**
     * Returns true if the passed mime type is supported by the helper.
     */
    public static boolean isSupportedArchiveType(String mimeType) {
        for (final String zipMimeType : ZIP_MIME_TYPES) {
            if (zipMimeType.equals(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a Uri for accessing an archive with the specified access mode.
     *
     * @see ParcelFileDescriptor#MODE_READ
     * @see ParcelFileDescriptor#MODE_WRITE
     */
    public static Uri buildUriForArchive(Uri externalUri, int accessMode) {
        return DocumentsContract.buildDocumentUri(AUTHORITY,
                new ArchiveId(externalUri, accessMode, "/").toDocumentId());
    }

    /**
     * Acquires an archive.
     */
    public static void acquireArchive(ContentProviderClient client, Uri archiveUri) {
        Archive.MorePreconditions.checkArgumentEquals(AUTHORITY, archiveUri.getAuthority(),
                "Mismatching authority. Expected: %s, actual: %s.");
        final String documentId = DocumentsContract.getDocumentId(archiveUri);

        try {
            client.call(METHOD_ACQUIRE_ARCHIVE, documentId, null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to acquire archive.", e);
        }
    }

    /**
     * Releases an archive.
     */
    public static void releaseArchive(ContentProviderClient client, Uri archiveUri) {
        Archive.MorePreconditions.checkArgumentEquals(AUTHORITY, archiveUri.getAuthority(),
                "Mismatching authority. Expected: %s, actual: %s.");
        final String documentId = DocumentsContract.getDocumentId(archiveUri);

        try {
            client.call(METHOD_RELEASE_ARCHIVE, documentId, null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to release archive.", e);
        }
    }

    /**
     * The archive won't close until all clients release it.
     */
    private void acquireArchive(String documentId) {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        synchronized (mArchives) {
            final Key key = Key.fromArchiveId(archiveId);
            Loader loader = mArchives.get(key);
            if (loader == null) {
                // TODO: Pass parent Uri so the loader can acquire the parent's notification Uri.
                loader = new Loader(getContext(), archiveId.mArchiveUri, archiveId.mAccessMode,
                        null);
                mArchives.put(key, loader);
            }
            loader.acquire();
            mArchives.put(key, loader);
        }
    }

    /**
     * If all clients release the archive, then it will be closed.
     */
    private void releaseArchive(String documentId) {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        final Key key = Key.fromArchiveId(archiveId);
        synchronized (mArchives) {
            final Loader loader = mArchives.get(key);
            loader.release();
            final int status = loader.getStatus();
            if (status == Loader.STATUS_CLOSED || status == Loader.STATUS_CLOSING) {
                mArchives.remove(key);
            }
        }
    }

    private Loader getLoaderOrThrow(String documentId) {
        final ArchiveId id = ArchiveId.fromDocumentId(documentId);
        final Key key = Key.fromArchiveId(id);
        synchronized (mArchives) {
            final Loader loader = mArchives.get(key);
            if (loader == null) {
                throw new IllegalStateException("Archive not acquired.");
            }
            return loader;
        }
    }

    private static class Key {
        Uri archiveUri;
        int accessMode;

        public Key(Uri archiveUri, int accessMode) {
            this.archiveUri = archiveUri;
            this.accessMode = accessMode;
        }

        public static Key fromArchiveId(ArchiveId id) {
            return new Key(id.mArchiveUri, id.mAccessMode);
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            return archiveUri.equals(((Key) other).archiveUri) &&
                accessMode == ((Key) other).accessMode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(archiveUri, accessMode);
        }
    }
}
