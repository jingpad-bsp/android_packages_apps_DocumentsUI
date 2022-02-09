/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.documentsui.queries;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.documentsui.base.SharedMinimal.DEBUG;

/**
 * A manager used to manage search history data.
 */
public class FavFileListDataManager {

    private static final String TAG = "FavFileListDataManager";

    private static final String[] PROJECTION_HISTORY = new String[]{
            DatabaseHelper.COLUMN_KEYWORD, DatabaseHelper.COLUMN_LAST_UPDATED_TIME
    };

    private static FavFileListDataManager sManager;
    private final DatabaseHelper mHelper;
    private final int mLimitedHistoryCount;
    @GuardedBy("mLock")
    private final List<String> mHistory = Collections.synchronizedList(new ArrayList<>());
    private final Object mLock = new Object();
    private FavDatabaseChangedListener mListener;

    public void setmResolver(ContentResolver mResolver) {
        this.mResolver = mResolver;
    }

    private ContentResolver mResolver;

    private enum DATABASE_OPERATION {
        QUERY, ADD, DELETE, UPDATE
    }

    private FavFileListDataManager(Context context) {
        mHelper = new DatabaseHelper(context);
        mLimitedHistoryCount = context.getResources().getInteger(
            R.integer.config_maximum_search_history);
    }

    /**
     * Get the singleton instance of SearchHistoryManager.
     *
     * @return the singleton instance, guaranteed not null
     */
    public static FavFileListDataManager getInstance(Context context) {
        synchronized (FavFileListDataManager.class) {
            if (sManager == null) {
                sManager = new FavFileListDataManager(context);
                sManager.new DatabaseTask(null, DATABASE_OPERATION.QUERY).executeOnExecutor(
                    AsyncTask.SERIAL_EXECUTOR);
            }
            return sManager;
        }
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 1;
        private static final String COLUMN_KEYWORD = "keyword";
        private static final String COLUMN_LAST_UPDATED_TIME = "last_updated_time";
        private static final String HISTORY_DATABASE = "fav_file_list.db";
        private static final String HISTORY_TABLE = "fav_file_list";

        private DatabaseHelper(Context context) {
            super(context, HISTORY_DATABASE, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + HISTORY_TABLE + " (" + COLUMN_KEYWORD + " TEXT NOT NULL, "
                + COLUMN_LAST_UPDATED_TIME + " INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            //TODO: Doing database backup/restore data migration or upgrade with b/121987495

            if (DEBUG) {
                Log.w(TAG, "Upgrading database..., Old version = " + oldVersion
                    + ", New version = " + newVersion);
            }
            db.execSQL("DROP TABLE IF EXISTS " + HISTORY_TABLE);
            onCreate(db);
        }
    }

    /**
     * Get search history list with/without filter text.
     * @param filter the filter text
     * @return a list of search history
     */
    public List<String> getFavList(@Nullable String filter) {
        synchronized (mLock) {
            if (!TextUtils.isEmpty(filter)) {
                final List<String> filterKeyword = Collections.synchronizedList(new ArrayList<>());
                final String keyword = filter;
                for (String history : mHistory) {
                    if (history.contains(keyword)) {
                        filterKeyword.add(history);
                    }
                }
                return filterKeyword;
            } else {
                return Collections.synchronizedList(new ArrayList<>(mHistory));
            }
        }
    }

    public boolean isFav(String path) {
        synchronized (mLock) {
            List<String> favList  = Collections.synchronizedList(new ArrayList<>(mHistory));
            return favList.contains(path);
        }
    }

    /**
     * Add search keyword text to list.
     * @param keyword the text to be added
     */
    public void addFav(String keyword) {//keyword = path or uri
        synchronized (mLock) {
            if (mHistory.remove(keyword)) {
                mHistory.add(0, keyword);
                new DatabaseTask(keyword, DATABASE_OPERATION.UPDATE).executeOnExecutor(
                    AsyncTask.SERIAL_EXECUTOR);
            } else {
                if (mHistory.size() >= mLimitedHistoryCount) {
                    new DatabaseTask(mHistory.remove(mHistory.size() - 1),
                        DATABASE_OPERATION.DELETE).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                        Boolean.FALSE);

                    Log.w(TAG, "Over search history count !! keyword = " + keyword
                        + "has been deleted");
                }
                mHistory.add(0, keyword);
                new DatabaseTask(keyword, DATABASE_OPERATION.ADD).executeOnExecutor(
                    AsyncTask.SERIAL_EXECUTOR);
            }
        }
    }

    /**
     * Delete search keyword text from list.
     * @param keyword the text to be deleted
     */
    public void deleteFav(String keyword) {
        synchronized (mLock) {
            if (mHistory.remove(keyword)) {
                new DatabaseTask(keyword, DATABASE_OPERATION.DELETE).executeOnExecutor(
                    AsyncTask.SERIAL_EXECUTOR);
            }
        }
    }

    private class DatabaseTask extends AsyncTask<Object, Void, Object> {
        private final String mKeyword;
        private final DATABASE_OPERATION mOperation;

        public DatabaseTask(String keyword, DATABASE_OPERATION operation) {
            mKeyword = keyword;
            mOperation = operation;
        }

        private Cursor getSortedHistoryList() {
            final SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
            queryBuilder.setTables(DatabaseHelper.HISTORY_TABLE);

            return queryBuilder.query(mHelper.getReadableDatabase(), PROJECTION_HISTORY, null,
                null, null, null, DatabaseHelper.COLUMN_LAST_UPDATED_TIME + " DESC");
        }

        private void addDatabaseData() {
            final ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COLUMN_KEYWORD, mKeyword);
            values.put(DatabaseHelper.COLUMN_LAST_UPDATED_TIME, System.currentTimeMillis());

            final long rowId = mHelper.getWritableDatabase().insert(
                DatabaseHelper.HISTORY_TABLE, null, values);
            if (rowId == -1) {
                Log.d("hjy", "Failed to add " + mKeyword + "to database!");
            }

            if (mListener != null) {
                mListener.onAddChangedListener(mKeyword);
            }
        }

        private void deleteDatabaseData() {
            // We only care about the field of DatabaseHelper.COLUMN_KEYWORD for deleting
            StringBuilder selection = new StringBuilder();
            selection.append(DatabaseHelper.COLUMN_KEYWORD).append("=?");
            final int numberOfRows = mHelper.getWritableDatabase().delete(
                DatabaseHelper.HISTORY_TABLE, selection.toString(), new String[] {
                    mKeyword });
            if (numberOfRows == 0) {
                Log.d("hjy", "Failed to delete " + mKeyword + "from database!");
            }

            if (mListener != null) {
                mListener.onDeleteChangedListener(mKeyword);
            }
        }

        private void updateDatabaseData() {
            // We just need to update the field DatabaseHelper.COLUMN_LAST_UPDATED_TIME,
            // because we will sort by last modified when retrieving from database
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COLUMN_LAST_UPDATED_TIME, System.currentTimeMillis());

            StringBuilder selection = new StringBuilder();
            selection.append(DatabaseHelper.COLUMN_KEYWORD).append("=?");
            final int numberOfRows = mHelper.getWritableDatabase().update(
                DatabaseHelper.HISTORY_TABLE, values, selection.toString(), new String[] {
                    mKeyword });
            if (numberOfRows == 0) {
                Log.d("hjy", "Failed to update " + mKeyword + "to database!");
            }
        }

        private void parseHistoryFromCursor(Cursor cursor) {
            if (cursor == null) {
                if (DEBUG) {
                    Log.e(TAG, "Null cursor happens when building local search history List!");
                }
                return;
            }
            synchronized (mLock) {
                mHistory.clear();
                try {
                    while (cursor.moveToNext()) {
                        mHistory.add(cursor.getString(cursor.getColumnIndex(
                            DatabaseHelper.COLUMN_KEYWORD)));
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        @Override
        protected Void doInBackground(Object... params) {
            if (!TextUtils.isEmpty(mKeyword)) {
                switch (mOperation) {
                    case ADD:
                        addDatabaseData();
                        break;
                    case DELETE:
                        deleteDatabaseData();
                        break;
                    case UPDATE:
                        updateDatabaseData();
                        break;
                    default:
                        break;
                }
            }

            // params[0] is used to preventing reload twice when deleting over history count
            if (params.length <= 0 || (params.length > 0 && ((Boolean)params[0]).booleanValue())) {
                parseHistoryFromCursor(getSortedHistoryList());
            }
            return null;
        }

        private void updateFavProvider() {
            final Uri uri = DocumentsContract.buildRootsUri("com.android.documentsui.fav");
            mResolver.notifyChange(uri, null, false);
        }

        @Override
        protected void onPostExecute(Object result) {
            if (mListener != null) {
                mListener.onPostExecute();
                updateFavProvider();
            }
        }
    }

    public void setDatabaseListener(FavDatabaseChangedListener listener) {
        mListener = listener;
    }

    public interface FavDatabaseChangedListener {
        void onAddChangedListener(String keyword);
        void onDeleteChangedListener(String keyword);
        void onPostExecute();
    }


}