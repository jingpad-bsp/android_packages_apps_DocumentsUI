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

package com.android.documentsui;

import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Menus;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.sidebar.RootsFragment;

import java.util.List;
import java.util.function.IntFunction;

public abstract class MenuManager {
    private final static String TAG = "MenuManager";

    final protected SearchViewManager mSearchManager;
    final protected State mState;
    final protected DirectoryDetails mDirDetails;

    protected Menu mOptionMenu;

    public MenuManager(
            SearchViewManager searchManager,
            State displayState,
            DirectoryDetails dirDetails) {
        mSearchManager = searchManager;
        mState = displayState;
        mDirDetails = dirDetails;
    }

    /** @see ActionModeController */
    public void updateActionMenu(Menu menu, SelectionDetails selection) {
        updateOpenWith(menu.findItem(R.id.action_menu_open_with), selection);
        updateDelete(menu.findItem(R.id.action_menu_delete), selection);
        updateShare(menu.findItem(R.id.action_menu_share), selection);
        updateRename(menu.findItem(R.id.action_menu_rename), selection);
        updateSelect(menu.findItem(R.id.action_menu_select), selection);
        updateSelectAll(menu.findItem(R.id.action_menu_select_all));
        updateMoveTo(menu.findItem(R.id.action_menu_move_to), selection);
        updateCopyTo(menu.findItem(R.id.action_menu_copy_to), selection);
        updateCompress(menu.findItem(R.id.action_menu_compress), selection);
        updateExtractTo(menu.findItem(R.id.action_menu_extract_to), selection);
        updateInspect(menu.findItem(R.id.action_menu_inspect), selection);
        updateViewInOwner(menu.findItem(R.id.action_menu_view_in_owner), selection);
        updateSort(menu.findItem(R.id.action_menu_sort));

        updateFav(menu.findItem(R.id.action_menu_fav), selection);//add by hjy
        updateUnfav(menu.findItem(R.id.action_menu_unfav), selection);//add by hjy

        Menus.disableHiddenItems(menu);
    }

    /** @see BaseActivity#onPrepareOptionsMenu */
    public void updateOptionMenu(Menu menu) {
        mOptionMenu = menu;
        updateOptionMenu();
    }

    public void updateOptionMenu() {
        if (mOptionMenu == null) {
            return;
        }
        updateCreateDir(mOptionMenu.findItem(R.id.option_menu_create_dir));
        updateGridOrList(mOptionMenu.findItem(R.id.option_menu_grid_or_list));
        updateSettings(mOptionMenu.findItem(R.id.option_menu_settings));
        updateSelectAll(mOptionMenu.findItem(R.id.option_menu_select_all));
//        updateNewWindow(mOptionMenu.findItem(R.id.option_menu_new_window));
        updateAdvanced(mOptionMenu.findItem(R.id.option_menu_advanced));
        updateDebug(mOptionMenu.findItem(R.id.option_menu_debug));
//        updateInspect(mOptionMenu.findItem(R.id.option_menu_inspect));
        updateSort(mOptionMenu.findItem(R.id.option_menu_sort));

        Menus.disableHiddenItems(mOptionMenu);
        mSearchManager.updateMenu();
    }

    public void updateSubMenu(Menu menu) {
        updateModePicker(menu.findItem(R.id.sub_menu_grid), menu.findItem(R.id.sub_menu_list));
    }

    public void updateModel(Model model) {}

    /**
     * Called when we needs {@link MenuManager} to ask Android to show context menu for us.
     * {@link MenuManager} can choose to defeat this request.
     *
     * {@link #inflateContextMenuForDocs} and {@link #inflateContextMenuForContainer} are called
     * afterwards when Android asks us to provide the content of context menus, so they're not
     * correct locations to suppress context menus.
     */
    public void showContextMenu(Fragment f, View v, float x, float y) {
        // Pickers don't have any context menu at this moment.
    }

    public void inflateContextMenuForContainer(Menu menu, MenuInflater inflater) {
        throw new UnsupportedOperationException("Pickers don't allow context menu.");
    }

    public void inflateContextMenuForDocs(
            Menu menu, MenuInflater inflater, SelectionDetails selectionDetails) {
        throw new UnsupportedOperationException("Pickers don't allow context menu.");
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to a file when the selection
     * doesn't contain any folder.
     *
     * @param selectionDetails
     *      containsFiles may return false because this may be called when user right clicks on an
     *      unselectable item in pickers
     */
    @VisibleForTesting
    public void updateContextMenuForFiles(Menu menu, SelectionDetails selectionDetails) {
        assert selectionDetails != null;

        MenuItem share = menu.findItem(R.id.dir_menu_share);
        MenuItem open = menu.findItem(R.id.dir_menu_open);
        MenuItem openWith = menu.findItem(R.id.dir_menu_open_with);
        MenuItem rename = menu.findItem(R.id.dir_menu_rename);
        MenuItem viewInOwner = menu.findItem(R.id.dir_menu_view_in_owner);

        updateShare(share, selectionDetails);
        updateOpenInContextMenu(open, selectionDetails);
        updateOpenWith(openWith, selectionDetails);
        updateRename(rename, selectionDetails);
        updateViewInOwner(viewInOwner, selectionDetails);

        updateContextMenu(menu, selectionDetails);
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to a folder when the selection
     * doesn't contain any file.
     *
     * @param selectionDetails
     *      containDirectories may return false because this may be called when user right clicks on
     *      an unselectable item in pickers
     */
    @VisibleForTesting
    public void updateContextMenuForDirs(Menu menu, SelectionDetails selectionDetails) {
        assert selectionDetails != null;

        MenuItem openInNewWindow = menu.findItem(R.id.dir_menu_open_in_new_window);
        MenuItem rename = menu.findItem(R.id.dir_menu_rename);
        MenuItem pasteInto = menu.findItem(R.id.dir_menu_paste_into_folder);

        updateOpenInNewWindow(openInNewWindow, selectionDetails);
        updateRename(rename, selectionDetails);
        updatePasteInto(pasteInto, selectionDetails);

        updateContextMenu(menu, selectionDetails);
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Update shared context menu items of both files and folders context menus.
     */
    @VisibleForTesting
    public void updateContextMenu(Menu menu, SelectionDetails selectionDetails) {
        assert selectionDetails != null;

        MenuItem cut = menu.findItem(R.id.dir_menu_cut_to_clipboard);
        MenuItem copy = menu.findItem(R.id.dir_menu_copy_to_clipboard);
        MenuItem delete = menu.findItem(R.id.dir_menu_delete);
        MenuItem inspect = menu.findItem(R.id.dir_menu_inspect);

        final boolean canCopy =
                selectionDetails.size() > 0 && !selectionDetails.containsPartialFiles();
        final boolean canDelete = selectionDetails.canDelete();
        cut.setEnabled(canCopy && canDelete);
        copy.setEnabled(canCopy);
        delete.setEnabled(canDelete);

        inspect.setEnabled(selectionDetails.size() == 1);
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to an empty pane.
     */
    @VisibleForTesting
    public void updateContextMenuForContainer(Menu menu) {
        MenuItem paste = menu.findItem(R.id.dir_menu_paste_from_clipboard);
        MenuItem selectAll = menu.findItem(R.id.dir_menu_select_all);
        MenuItem createDir = menu.findItem(R.id.dir_menu_create_dir);
        MenuItem inspect = menu.findItem(R.id.dir_menu_inspect);
//        MenuItem gridOrList = menu.findItem(R.id.dir_menu_g)

        paste.setEnabled(mDirDetails.hasItemsToPaste() && mDirDetails.canCreateDoc());
        updateSelectAll(selectAll);
        updateCreateDir(createDir);
//        updateGridOrList()
        updateInspect(inspect);
    }

    /**
     * @see RootsFragment#onCreateContextMenu
     */
    public void updateRootContextMenu(Menu menu, RootInfo root, DocumentInfo docInfo) {
        MenuItem eject = menu.findItem(R.id.root_menu_eject_root);
        MenuItem pasteInto = menu.findItem(R.id.root_menu_paste_into_folder);
        MenuItem openInNewWindow = menu.findItem(R.id.root_menu_open_in_new_window);
        MenuItem settings = menu.findItem(R.id.root_menu_settings);

        updateEject(eject, root);
        updatePasteInto(pasteInto, root, docInfo);
        updateOpenInNewWindow(openInNewWindow, root);
        updateSettings(settings, root);
    }

    public abstract void updateKeyboardShortcutsMenu(
            List<KeyboardShortcutGroup> data, IntFunction<String> stringSupplier);

    protected void updateModePicker(MenuItem grid, MenuItem list) {
        grid.setVisible(mState.derivedMode != State.MODE_GRID);
        list.setVisible(mState.derivedMode != State.MODE_LIST);
    }

    protected void updateAdvanced(MenuItem advanced) {
        advanced.setVisible(mState.showDeviceStorageOption);
        advanced.setTitle(mState.showDeviceStorageOption && mState.showAdvanced
                ? R.string.menu_advanced_hide : R.string.menu_advanced_show);
    }

    protected void updateSort(MenuItem sort) {
        sort.setVisible(true);
    }

    protected void updateDebug(MenuItem debug) {
        debug.setVisible(mState.debugMode);
    }

    protected void updateSettings(MenuItem settings) {
        settings.setVisible(false);
    }

    protected void updateSettings(MenuItem settings, RootInfo root) {
        settings.setVisible(false);
    }

    protected void updateEject(MenuItem eject, RootInfo root) {
        eject.setVisible(false);
    }

    protected void updateNewWindow(MenuItem newWindow) {
        newWindow.setVisible(false);
    }

    protected void updateSelect(MenuItem select, SelectionDetails selectionDetails) {
        select.setVisible(false);
    }

    protected void updateOpenWith(MenuItem openWith, SelectionDetails selectionDetails) {
        openWith.setVisible(false);
    }

    protected void updateOpenInNewWindow(
            MenuItem openInNewWindow, SelectionDetails selectionDetails) {
        openInNewWindow.setVisible(false);
    }

    protected void updateOpenInNewWindow(
            MenuItem openInNewWindow, RootInfo root) {
        openInNewWindow.setVisible(false);
    }

    protected void updateShare(MenuItem share, SelectionDetails selectionDetails) {
        share.setVisible(false);
    }

    protected void updateFav(MenuItem fav, SelectionDetails selectionDetails) {//add by hjy
        fav.setVisible(false);
    }

    protected void updateUnfav(MenuItem unfav, SelectionDetails selectionDetails) {//add by hjy
        unfav.setVisible(false);
    }

    protected void updateDelete(MenuItem delete, SelectionDetails selectionDetails) {
        delete.setVisible(false);
    }

    protected void updateRename(MenuItem rename, SelectionDetails selectionDetails) {
        rename.setVisible(false);
    }

    /**
     * This method is called for standard activity option menu as opposed
     * to when there is a selection.
     */
    protected void updateInspect(MenuItem inspector) {
        inspector.setVisible(false);
    }

    /**
     * This method is called for action mode, when a selection exists.
     */
    protected void updateInspect(MenuItem inspect, SelectionDetails selectionDetails) {
        inspect.setVisible(false);
    }

    protected void updateViewInOwner(MenuItem view, SelectionDetails selectionDetails) {
        view.setVisible(false);
    }

    protected void updateMoveTo(MenuItem moveTo, SelectionDetails selectionDetails) {
        moveTo.setVisible(false);
    }

    protected void updateCopyTo(MenuItem copyTo, SelectionDetails selectionDetails) {
        copyTo.setVisible(false);
    }

    protected void updateCompress(MenuItem compress, SelectionDetails selectionDetails) {
        compress.setVisible(false);
    }

    protected void updateExtractTo(MenuItem extractTo, SelectionDetails selectionDetails) {
        extractTo.setVisible(false);
    }

    protected void updatePasteInto(MenuItem pasteInto, SelectionDetails selectionDetails) {
        pasteInto.setVisible(false);
    }

    protected void updatePasteInto(MenuItem pasteInto, RootInfo root, DocumentInfo docInfo) {
        pasteInto.setVisible(false);
    }

    protected void updateOpenInContextMenu(MenuItem open, SelectionDetails selectionDetails) {
        open.setVisible(false);
    }

    protected abstract void updateSelectAll(MenuItem selectAll);
    protected abstract void updateCreateDir(MenuItem createDir);
    protected abstract void updateGridOrList(MenuItem gridOrList);

    /**
     * Access to meta data about the selection.
     */
    public interface SelectionDetails {
        boolean containsDirectories();

        boolean containsFiles();

        int size();

        boolean containsPartialFiles();

        boolean containsFilesInArchive();

        // TODO: Update these to express characteristics instead of answering concrete questions,
        // since the answer to those questions is (or can be) activity specific.
        boolean canDelete();

        boolean canRename();

        boolean canPasteInto();

        boolean canExtract();

        boolean canOpenWith();

        boolean canViewInOwner();

        boolean isFavFile();
    }

    public static class DirectoryDetails {
        private final BaseActivity mActivity;

        public DirectoryDetails(BaseActivity activity) {
            mActivity = activity;
        }

        public boolean hasRootSettings() {
            return mActivity.getCurrentRoot().hasSettings();
        }

        public boolean hasItemsToPaste() {
            return false;
        }

        public boolean canCreateDoc() {
            /* bug 1475477 directory maybe nulll @{*/
            DocumentInfo directory = mActivity.getCurrentDirectory();
            if (directory == null) {
                return false;
            }
            return isInRecents() ? false : directory.isCreateSupported();
            /*}@*/
        }

        public boolean isInRecents() {
            return mActivity.isInRecents();
        }

        public boolean canCreateDirectory() {
            return mActivity.canCreateDirectory();
        }

        public boolean canInspectDirectory() {
            return mActivity.canInspectDirectory() && !isInRecents();
        }
    }
}
