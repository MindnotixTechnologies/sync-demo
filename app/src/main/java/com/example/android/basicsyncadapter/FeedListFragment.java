/*
 * Copyright 2013 The Android Open Source Project
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

package com.example.android.basicsyncadapter;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.example.android.basicsyncadapter.account.AccountUtils;
import com.example.android.basicsyncadapter.provider.FeedContract;
import com.example.android.basicsyncadapter.sync.SyncService;
import com.example.android.basicsyncadapter.sync.SyncUtils;

/**
 * List fragment containing a list of Atom entry objects (articles) stored in the local database.
 *
 * <p>Database access is mediated by a content provider, specified in
 * {@link com.example.android.basicsyncadapter.provider.FeedProvider}. This content
 * provider is
 * automatically populated by  {@link SyncService}.
 *
 * <p>Selecting an item from the displayed list displays the article in the default browser.
 *
 * <p>If the content provider doesn't return any data, then the first sync hasn't run yet. This sync
 * adapter assumes data exists in the provider once a sync has run. If your app doesn't work like
 * this, you should add a flag that notes if a sync has run, so you can differentiate between "no
 * available data" and "no initial sync", and display this in the UI.
 *
 * <p>The ActionBar displays a "Refresh" button. When the user clicks "Refresh", the sync adapter
 * runs immediately. An indeterminate ProgressBar element is displayed, showing that the sync is
 * occurring.
 */
public class FeedListFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "FeedListFragment";

    /**
     * Cursor adapter for controlling ListView results.
     */
    private SimpleCursorAdapter mAdapter;

    /**
     * Handle to a SyncObserver. The ProgressBar element is visible until the SyncObserver reports
     * that the sync is complete.
     *
     * <p>This allows us to delete our SyncObserver once the application is no longer in the
     * foreground.
     */
    private Object mSyncObserverHandle;

    /**
     * Options menu used to populate ActionBar.
     */
    private Menu mOptionsMenu;

    /**
     * Projection for querying the content provider.
     */
    private static final String[] PROJECTION = new String[]{
            FeedContract.Entry._ID,
            FeedContract.Entry.COLUMN_NAME_TITLE,
            FeedContract.Entry.COLUMN_NAME_LINK,
            FeedContract.Entry.COLUMN_NAME_PUBLISHED
    };

    // Column indexes. The index of a column in the Cursor is the same as its relative position in
    // the projection.
    /**
     * Column index for _ID
     */
    private static final int COLUMN_ID = 0;
    /**
     * Column index for title
     */
    private static final int COLUMN_TITLE = 1;
    /**
     * Column index for link
     */
    private static final int COLUMN_URL_STRING = 2;
    /**
     * Column index for published
     */
    private static final int COLUMN_PUBLISHED = 3;

    private ListView mListView;
    private View mEmpty;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public FeedListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    /**
     * Create SyncAccount at launch, if needed.
     *
     * <p>This will create a new account with the system for our application, register our
     * {@link SyncService} with it, and establish a sync schedule.
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Create account, if needed
        SyncUtils.createSyncAccount(context);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed_list, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mListView = (ListView) view.findViewById(R.id.list_view);
        mEmpty = view.findViewById(R.id.empty);

        mAdapter = new SimpleCursorAdapter(
                getActivity(),       // Current context
                R.layout.list_item,  // Layout for individual rows
                null,                // Cursor
                new String[]{        // Cursor columns to use
                                     FeedContract.Entry.COLUMN_NAME_TITLE,
                                     FeedContract.Entry.COLUMN_NAME_PUBLISHED
                },
                new int[]{           // Layout fields to use
                                     R.id.title,
                                     R.id.date},
                0                    // No flags
        );
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int i) {
                if (i == COLUMN_PUBLISHED) {
                    // Convert timestamp to human-readable date
                    Time t = new Time();
                    t.set(cursor.getLong(i));
                    ((TextView) view).setText(t.format("%Y-%m-%d %H:%M"));
                    return true;
                } else {
                    // Let SimpleCursorAdapter handle other fields automatically
                    return false;
                }
            }
        });

        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Load an article in the default browser when selected by the user.

                // Get a URI for the selected item, then start an Activity that displays the URI. Any
                // Activity that filters for ACTION_VIEW and a URI can accept this. In most cases, this will
                // be a browser.

                // Get the item at the selected position, in the form of a Cursor.
                Cursor c = (Cursor) mAdapter.getItem(position);
                // Get the link to the article represented by the item.
                String articleUrlString = c.getString(COLUMN_URL_STRING);
                if (articleUrlString == null) {
                    Log.e(TAG, "Attempt to launch entry with null link");
                    return;
                }

                Log.i(TAG, "Opening URL: " + articleUrlString);
                // Get a Uri object for the URL string
                Uri articleURL = Uri.parse(articleUrlString);
                Intent i = new Intent(Intent.ACTION_VIEW, articleURL);
                startActivity(i);
            }
        });

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSyncStatusObserver.onStatusChanged(0);

        // Watch for sync state changes
        final int mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING |
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;
        mSyncObserverHandle = ContentResolver.addStatusChangeListener(mask, mSyncStatusObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSyncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
            mSyncObserverHandle = null;
        }
    }

    /**
     * Create the ActionBar.
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        mOptionsMenu = menu;
        inflater.inflate(R.menu.list, menu);
    }

    /**
     * Respond to user gestures on the ActionBar.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // If the user clicks the "Refresh" button.
            case R.id.menu_refresh:
                SyncUtils.triggerRefresh();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Query the content provider for data.
     *
     * <p>Loaders do queries in a background thread. They also provide a ContentObserver that is
     * triggered when data in the content provider changes. When the sync adapter updates the
     * content provider, the ContentObserver responds by resetting the loader and then reloading
     * it.
     */
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // We only have one loader, so we can ignore the value of i.
        // (It'll be '0', as set in onCreate().)
        return new CursorLoader(getActivity(),  // Context
                FeedContract.Entry.CONTENT_URI, // URI
                PROJECTION,                     // Projection
                null,                           // Selection
                null,                           // Selection args
                FeedContract.Entry.COLUMN_NAME_PUBLISHED + " desc"); // Sort
    }

    private void changeCursor(Cursor cursor) {
        mAdapter.changeCursor(cursor);
        mEmpty.setVisibility(cursor == null || cursor.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Move the Cursor returned by the query into the ListView adapter. This refreshes the existing
     * UI with the data in the Cursor.
     */
    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        changeCursor(cursor);
    }

    /**
     * Called when the ContentObserver defined for the content provider detects that data has
     * changed. The ContentObserver resets the loader, and then re-runs the loader. In the adapter,
     * set the Cursor value to null. This removes the reference to the Cursor, allowing it to be
     * garbage-collected.
     */
    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        changeCursor(null);
    }

    /**
     * Set the state of the Refresh button. If a sync is active, turn on the ProgressBar widget.
     * Otherwise, turn it off.
     *
     * @param refreshing True if an active sync is occuring, false otherwise
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setRefreshActionButtonState(boolean refreshing) {
        if (mOptionsMenu == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return;
        }

        final MenuItem refreshItem = mOptionsMenu.findItem(R.id.menu_refresh);
        if (refreshItem != null) {
            if (refreshing) {
                refreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
            } else {
                refreshItem.setActionView(null);
            }
        }
    }

    /**
     * Crfate a new anonymous SyncStatusObserver. It's attached to the app's ContentResolver in
     * onResume(), and removed in onPause(). If status changes, it sets the state of the Refresh
     * button. If a sync is active or pending, the Refresh button is replaced by an indeterminate
     * ProgressBar; otherwise, the button itself is displayed.
     */
    private SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        /** Callback invoked with the sync adapter status changes. */
        @Override
        public void onStatusChanged(int which) {
            getActivity().runOnUiThread(new Runnable() {
                /**
                 * The SyncAdapter runs on a background thread. To update the UI, onStatusChanged()
                 * runs on the UI thread.
                 */
                @Override
                public void run() {
                    // Create a handle to the account that was created by
                    // SyncService.createSyncAccount(). This will be used to query the system to
                    // see how the sync status has changed.
                    Account account = AccountUtils.getAccount();

                    // Test the ContentResolver to see if the sync adapter is active or pending.
                    // Set the state of the refresh button accordingly.
                    boolean syncActive = ContentResolver.isSyncActive(
                            account, FeedContract.CONTENT_AUTHORITY);
                    boolean syncPending = ContentResolver.isSyncPending(
                            account, FeedContract.CONTENT_AUTHORITY);
                    setRefreshActionButtonState(syncActive || syncPending);
                }
            });
        }
    };

}