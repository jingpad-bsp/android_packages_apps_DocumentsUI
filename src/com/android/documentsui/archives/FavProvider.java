package com.android.documentsui.archives;

import androidx.annotation.Nullable;
import android.content.UriPermission;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.GuardedBy;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.queries.FavFileListDataManager;
import com.android.internal.content.FileSystemProvider;

import java.io.File;
import java.io.FileNotFoundException;
import android.provider.DocumentsContract.Path;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Adds an entry for traces in the file picker.
 */
public class FavProvider extends FileSystemProvider implements FavFileListDataManager.FavDatabaseChangedListener {

    public static final String TAG = FavProvider.class.getName();
    public static final String AUTHORITY = "com.android.documentsui.fav";

    private final Object mRootsLock = new Object();

    @GuardedBy("mRootsLock")
    private ArrayMap<String, RootInfo> mRoots = new ArrayMap<>();


    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_DOCUMENT_ID,
    };

    public FavProvider() {};
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
    };

    @Override
    public boolean onCreate() {
        super.onCreate(DEFAULT_DOCUMENT_PROJECTION);
        FavFileListDataManager.getInstance(getContext()).setDatabaseListener(this);
        FavFileListDataManager.getInstance(getContext()).setmResolver(getContext().getContentResolver());
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        synchronized (mRootsLock) {
            for (RootInfo root : mRoots.values()) {
                final MatrixCursor.RowBuilder row2 = result.newRow();
                row2.add(Root.COLUMN_ROOT_ID, root.rootId);
                row2.add(Root.COLUMN_FLAGS, root.flags);
                row2.add(Root.COLUMN_TITLE, root.title);
                row2.add(Root.COLUMN_DOCUMENT_ID, root.documentId);
                row2.add(Root.COLUMN_QUERY_ARGS, SUPPORTED_QUERY_ARGS);
            }
        }
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        return super.queryDocument(documentId, projection);
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
           throws FileNotFoundException {
        return super.queryChildDocuments(parentDocumentId, projection, sortOrder);
    }

    @Override
    public Path findDocumentPath(@Nullable String parentDocId, String childDocId)
            throws FileNotFoundException {
        final Pair<RootInfo, File> resolvedDocId = resolveDocId(childDocId, false);
        final RootInfo root = resolvedDocId.first;
        File child = resolvedDocId.second;

        final File parent = TextUtils.isEmpty(parentDocId)
                ? root.path
                : getFileForDocId(parentDocId);

        return new Path(parentDocId == null ? root.rootId : null, findDocumentPath(parent, child));
    }

    private Uri getDocumentUri(String path, List<UriPermission> accessUriPermissions)
            throws FileNotFoundException {
        File doc = new File(path);

        final String docId = getDocIdForFile(doc);

        UriPermission docUriPermission = null;
        UriPermission treeUriPermission = null;
        for (UriPermission uriPermission : accessUriPermissions) {
            final Uri uri = uriPermission.getUri();
            if (AUTHORITY.equals(uri.getAuthority())) {
                boolean matchesRequestedDoc = false;
                if (DocumentsContract.isTreeUri(uri)) {
                    final String parentDocId = DocumentsContract.getTreeDocumentId(uri);
                    if (isChildDocument(parentDocId, docId)) {
                        treeUriPermission = uriPermission;
                        matchesRequestedDoc = true;
                    }
                } else {
                    final String candidateDocId = DocumentsContract.getDocumentId(uri);
                    if (Objects.equals(docId, candidateDocId)) {
                        docUriPermission = uriPermission;
                        matchesRequestedDoc = true;
                    }
                }

                if (matchesRequestedDoc && allowsBothReadAndWrite(uriPermission)) {
                    // This URI permission provides everything an app can get, no need to
                    // further check any other granted URI.
                    break;
                }
            }
        }

        // Full permission URI first.
        if (allowsBothReadAndWrite(treeUriPermission)) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUriPermission.getUri(), docId);
        }

        if (allowsBothReadAndWrite(docUriPermission)) {
            return docUriPermission.getUri();
        }

        // Then partial permission URI.
        if (treeUriPermission != null) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUriPermission.getUri(), docId);
        }

        if (docUriPermission != null) {
            return docUriPermission.getUri();
        }

        throw new SecurityException("The app is not given any access to the document under path " +
                path + " with permissions granted in " + accessUriPermissions);
    }

    private static boolean allowsBothReadAndWrite(UriPermission permission) {
        return permission != null
                && permission.isReadPermission()
                && permission.isWritePermission();
    }

    @Override
    public Cursor querySearchDocuments(String rootId, String[] projection, Bundle queryArgs)
            throws FileNotFoundException {
        final File parent;
        synchronized (mRootsLock) {
            parent = mRoots.get(rootId).path;
        }

        return querySearchDocuments(parent, projection, Collections.emptySet(), queryArgs);
    }

    @Override
    protected String getDocIdForFile(File file) throws FileNotFoundException {
        return getDocIdForFileMaybeCreate(file, false);
    }

    private String getDocIdForFileMaybeCreate(File file, boolean createNewDir)
            throws FileNotFoundException {
        String path = file.getAbsolutePath();

        // Find the most-specific root path
        RootInfo mostSpecificRoot = getMostSpecificRootForPath(path, false);

        if (mostSpecificRoot == null) {
            // Try visible path if no internal path matches. MediaStore uses visible paths.
            mostSpecificRoot = getMostSpecificRootForPath(path, true);
        }

        if (mostSpecificRoot == null) {
            throw new FileNotFoundException("Failed to find root that contains " + path);
        }

        final String rootPath = mostSpecificRoot.path.getAbsolutePath();
        if (rootPath.equals(path)) {
            path = "";
        } else if (rootPath.endsWith("/")) {
            path = path.substring(rootPath.length());
        } else {
            path = path.substring(rootPath.length() + 1);
        }

        if (!file.exists() && createNewDir) {
            Log.i(TAG, "Creating new directory " + file);
            if (!file.mkdir()) {
                Log.e(TAG, "Could not create directory " + file);
            }
        }

        return mostSpecificRoot.rootId + ':' + path;
    }

    private RootInfo getMostSpecificRootForPath(String path, boolean visible) {
        // Find the most-specific root path
        RootInfo mostSpecificRoot = null;
        String mostSpecificPath = null;
        synchronized (mRootsLock) {
            for (int i = 0; i < mRoots.size(); i++) {
                final RootInfo root = mRoots.valueAt(i);
//                final File rootFile = visible ? root.visiblePath : root.path;
				final File rootFile = root.path;
                if (rootFile != null) {
                    final String rootPath = rootFile.getAbsolutePath();
                    if (path.startsWith(rootPath) && (mostSpecificPath == null
                            || rootPath.length() > mostSpecificPath.length())) {
                        mostSpecificRoot = root;
                        mostSpecificPath = rootPath;
                    }
                }
            }
        }

        return mostSpecificRoot;
    }

    @Override
    protected File getFileForDocId(String docId, boolean visible) throws FileNotFoundException {
        return getFileForDocId(docId, visible, true);
    }

    private File getFileForDocId(String docId, boolean visible, boolean mustExist)
            throws FileNotFoundException {
        RootInfo root = getRootFromDocId(docId);
        return buildFile(root, docId, visible, mustExist);
    }

    private Pair<RootInfo, File> resolveDocId(String docId, boolean visible)
            throws FileNotFoundException {
        RootInfo root = getRootFromDocId(docId);
        return Pair.create(root, buildFile(root, docId, visible, true));
    }

    private RootInfo getRootFromDocId(String docId) throws FileNotFoundException {
        final int splitIndex = docId.indexOf(':', 1);
        final String tag = docId.substring(0, splitIndex);

        RootInfo root;
        synchronized (mRootsLock) {
            root = mRoots.get(tag);
        }
        if (root == null) {
            throw new FileNotFoundException("No root for " + tag);
        }

        return root;
    }

    private File buildFile(RootInfo root, String docId, boolean visible, boolean mustExist)
            throws FileNotFoundException {
        final int splitIndex = docId.indexOf(':', 1);
        final String path = docId.substring(splitIndex + 1);

		File target = root.path;
        if (target == null) {
            return null;
        }
        if (!target.exists()) {
            target.mkdirs();
        }
        target = new File(target, path);
        if (mustExist && !target.exists()) {
            throw new FileNotFoundException("Missing file for " + docId + " at " + target);
        }
        return target;
    }

    @Override
    protected Uri buildNotificationUri(String docId) {
        return DocumentsContract.buildChildDocumentsUri(AUTHORITY, docId);
    }

    @Override
    protected void onDocIdChanged(String docId) {
        try {
            // Touch the visible path to ensure that any sdcardfs caches have
            // been updated to reflect underlying changes on disk.
            final File visiblePath = getFileForDocId(docId, true, false);
            if (visiblePath != null) {
                Os.access(visiblePath.getAbsolutePath(), OsConstants.F_OK);
            }
        } catch (FileNotFoundException | ErrnoException ignored) {
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle bundle = super.call(method, arg, extras);
        if (bundle == null && !TextUtils.isEmpty(method)) {
            switch (method) {
                case "getDocIdForFileCreateNewDir": {
                    getContext().enforceCallingPermission(
                            android.Manifest.permission.MANAGE_DOCUMENTS, null);
                    if (TextUtils.isEmpty(arg)) {
                        return null;
                    }
                    try {
                        final String docId = getDocIdForFileMaybeCreate(new File(arg), true);
                        bundle = new Bundle();
                        bundle.putString("DOC_ID", docId);
                    } catch (FileNotFoundException e) {
                        Log.w(TAG, "file '" + arg + "' not found");
                        return null;
                    }
                    break;
                }
                case "get_document_uri": {
                    // All callers must go through MediaProvider
                    getContext().enforceCallingPermission(
                            android.Manifest.permission.WRITE_MEDIA_STORAGE, TAG);

                    final Uri fileUri = extras.getParcelable(DocumentsContract.EXTRA_URI);
                    final List<UriPermission> accessUriPermissions = extras
                            .getParcelableArrayList(DocumentsContract.EXTRA_URI_PERMISSIONS);

                    final String path = fileUri.getPath();
                    try {
                        final Bundle out = new Bundle();
                        final Uri uri = getDocumentUri(path, accessUriPermissions);
                        out.putParcelable(DocumentsContract.EXTRA_URI, uri);
                        return out;
                    } catch (FileNotFoundException e) {
                        throw new IllegalStateException("File in " + path + " is not found.", e);
                    }
                }
                case "get_media_uri": {
                    // All callers must go through MediaProvider
                    getContext().enforceCallingPermission(
                            android.Manifest.permission.WRITE_MEDIA_STORAGE, TAG);

                    final Uri documentUri = extras.getParcelable(DocumentsContract.EXTRA_URI);
                    final String docId = DocumentsContract.getDocumentId(documentUri);
                    try {
                        final Bundle out = new Bundle();
                        final Uri uri = Uri.fromFile(getFileForDocId(docId, true));
                        out.putParcelable(DocumentsContract.EXTRA_URI, uri);
                        return out;
                    } catch (FileNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                }
                default:
                    Log.w(TAG, "unknown method passed to call(): " + method);
            }
        }
        return bundle;
    }


    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    @Override
    public void onAddChangedListener(String keyword) {
        RootInfo root = new RootInfo();
        root.rootId = keyword.substring(keyword.indexOf("/storage/emulated/0/") + 1);;
        root.title = keyword.substring(keyword.lastIndexOf("/") + 1);
        root.documentId = root.rootId + ":";
        root.flags = Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_SEARCH
                | Root.FLAG_SUPPORTS_IS_CHILD;
        root.path = new File(keyword);
        if (root.path.canWrite()) {
            root.flags |= Root.FLAG_SUPPORTS_CREATE;
        }
        mRoots.put(root.rootId, root);
    }

    @Override
    public void onDeleteChangedListener(String keyword) {
        RootInfo root = new RootInfo();
        root.rootId = keyword.substring(keyword.indexOf("/storage/emulated/0/") + 1);;
        mRoots.remove(root.rootId);
    }

    boolean isFirst = true;
    @Override
    public void onPostExecute() {//启动后初始化收藏数据
        if(isFirst){
            isFirst = false;
            List<String> favList = FavFileListDataManager.getInstance(getContext()).getFavList("");
            for(int i = 0; i < favList.size(); i++) {
                String path = favList.get(i);
                RootInfo root = new RootInfo();
                root.rootId = path.substring(path.indexOf("/storage/emulated/0/") + 1);;
                root.title = path.substring(path.lastIndexOf("/") + 1);
                root.documentId = root.rootId + ":";
                root.flags = Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_SEARCH
                        | Root.FLAG_SUPPORTS_IS_CHILD;
                root.path = new File(path);
                if (root.path.canWrite()) {
                    root.flags |= Root.FLAG_SUPPORTS_CREATE;
                }
                mRoots.put(root.rootId, root);
            }
        }
    }
}
