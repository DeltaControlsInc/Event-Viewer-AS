/* Copyright (c) 2014, Delta Controls Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, 
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this 
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice, this 
list of conditions and the following disclaimer in the documentation and/or other 
materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors may 
be used to endorse or promote products derived from this software without specific 
prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.
*/
package com.deltacontrols.eventviewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.deltacontrols.eventviewer.MoreIndicator.Direction;
import com.deltacontrols.eventviewer.controls.EventDetailView;
import com.deltacontrols.eventviewer.service.EventNotificationsService;
import com.deltacontrols.eventviewer.service.ScheduleEventNotifications;
import com.deltacontrols.eweb.support.api.FetchXML;
import com.deltacontrols.eweb.support.api.FetchXML.Result;
import com.deltacontrols.eweb.support.interfaces.GenericCallback;
import com.deltacontrols.eweb.support.models.AlarmGroup;
import com.deltacontrols.eweb.support.models.UserList;
import com.deltacontrols.eweb.support.models.iEvent;
import com.deltacontrols.eweb.support.models.iEvent.iAlarmDetails;

/**
 * Main Activity for the EventViewer application; it contains the main list view of all events as well as the
 * filter and event details dialog popup. Uses single view + dialog to avoid having to pass data around between activities and fragments.
 * 
 * === Service Interaction ===
 * On login, EventViewer will force the service to start (to get an immediate update and to make sure the service is running).
 * EventViewer (onResume) will attempt to bind with the service creating a ServiceConnection which will give access to public 
 * functionality of the service.
 * 
 * Sets up an EventBroadcastReceiver which listens for the NOTIFICATION_INTENT_ACTION broadcast from EventNotificationService. 
 * This tells MainActivity it needs to update its data.
 * 
 * On log out, EventViewer calls logout on the service, forcing the cache to be reset and for the service to stop repeating. 
 * No more requests should be made to the server until the next login
 */
public class MainActivity extends Activity {

    // ------------------------------------------------------------------------------
    // EventBroadcastReciever
    // Listen for event notification from the background service
    // ------------------------------------------------------------------------------
    private class EventBroadcastReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (mService == null) {
                Log.e(App.TAG, "EventBroadcastReciever onReceive mNotificationService is null");
                return;
            }

            // Check for error state
            EventNotificationsService.STATUS status = mService.getCurrentStatus();
            if (status.equals(EventNotificationsService.STATUS.OK)) {
                if (mAutoUpdate || (mListItemsArray.size() == 0)) {
                    // If auto updating, OR we have no current data, then sync
                    syncWithDataFromService();
                }
                else {
                    // Else, simply update the new events text
                    updateNewEventsText();
                }
            }

            updateStatusMessage();
        }
    }

    private EventBroadcastReciever evReceiver; // Setup notification receiver from service

    // ------------------------------------------------------------------------------
    // EventNotificationsService binding
    // ------------------------------------------------------------------------------
    private ServiceWrapper mService;
    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder binder) {
            mService.setService(((EventNotificationsService.MyBinder) binder).getService());

            updateStatusMessage();

            // If list currently empty, load with data from service.
            if (mListItemsArray.size() == 0) {
                syncWithDataFromService(); // Update with cache first

                // Force the service to run immediately to get the newest data
                ScheduleEventNotifications.stopServiceRepeating(MainActivity.this);
                ScheduleEventNotifications.startServiceRepeating(MainActivity.this);
            }
            else {
                updateNewEventsText();
            }

        }

        public void onServiceDisconnected(ComponentName className) {
            mService.setService(null);
        }
    };

    // ------------------------------------------------------------------------------
    // Static properties
    // ------------------------------------------------------------------------------
    static final String GROUP_SELECTED_POSITION = "EV_GROUP_SELECTED_POSITION"; // Save state for alarm group selection
    static final String AUTO_UPDATE = "EV_AUTO_UPDATE"; // Save state for pause/play

    // ------------------------------------------------------------------------------
    // Outlets
    // ------------------------------------------------------------------------------
    private ListView mEventList;            // Main list of events

    private Spinner mFilterGroupSpinner;    // Alarm group spinner (filter)
    private View mEventListFilters;         // View containing all filters
    private EditText mFilterName;           // Event name (filter)

    private MenuItem mRefreshMenuItem;      // Refresh icon (menu)
    private MenuItem mAutoUpdateItem;       // Pause/Play icon (menu)
    private MenuItem mFilterItem;           // Filter icon (menu)

    private TextView mStatusMessage;        // Status text (status bar)
    private ImageView mStatusEwebConnection;// Status icon (status bar)
    private View mEventListFauxHeader;      // List footer indicating "more up"
    private View mEventListFauxFooter;      // List footer indicating "more down"

    // ------------------------------------------------------------------------------
    // Private properties
    // ------------------------------------------------------------------------------
    private Context mCtx;                   // App context
    private boolean mAutoUpdate;            // If list is currently auto updating when service broadcast is received
    private EventItemAdapter mListAdapter;  // Event list adapter
    private ArrayList<iEvent> mListItemsArray;      // Event list data array
    private GroupSpinnerItemAdapter mGroupAdapter;  // Alarm group adapter
    private ArrayList<AlarmGroup> mGroupItemsArray; // Alarm group data array
    private int mGroupCurrentSelectedPos;   // Currently selected alarm group
    private UserList mUserList;             // List of users that we can assign-to
    private boolean mFilterApplied;         // Indicates if a filter is currently being applied
    private boolean isHeaderVisible;        // Indicates if mEventListFauxHeader should be visible
    private boolean isFooterVisible;        // Indicates if mEventListFauxFooter should be visible
    private iEvent mSelectedEvent;          // The currently selected event (could be null if nothing selected)
    private Dialog mDialog;                 // Reference to the event details dialog (could be null if dialog not showing)
    private ArrayAdapter<String> mDialogUserListAdapter;
    private String mGroupNameForAllEvents;  // String for "<All>" in the group dropdown

    // ------------------------------------------------------------------------------
    // Life Cycle
    // ------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCtx = this;
        evReceiver = new EventBroadcastReciever();
        mService = new ServiceWrapper();
        mSelectedEvent = null;
        mFilterApplied = false;
        mDialog = null;
        mGroupNameForAllEvents = getString(R.string.group_name_all);
        clearView();

        // Check bundle for saved state
        if (savedInstanceState != null) {
            // Restore value of members from saved state
            mGroupCurrentSelectedPos = savedInstanceState.getInt(GROUP_SELECTED_POSITION);
            mAutoUpdate = savedInstanceState.getBoolean(AUTO_UPDATE);
        }
        else {
            mGroupCurrentSelectedPos = 0;
            mAutoUpdate = false;
        }

        // Get outlets
        mEventListFauxHeader = findViewById(R.id.eventListFauxHeader);
        ((MoreIndicator) mEventListFauxHeader).setDirection(Direction.UP);
        mEventListFauxHeader.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mEventList.smoothScrollToPosition(0);
            }
        });

        mEventListFauxFooter = findViewById(R.id.eventListFauxFooter);
        mEventListFauxFooter.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mEventList.smoothScrollToPosition(mEventList.getCount());
            }
        });

        mStatusMessage = (TextView) findViewById(R.id.statusMessage);
        mStatusEwebConnection = (ImageView) findViewById(R.id.statusEwebConnection);
        mEventListFilters = findViewById(R.id.eventListFilters);

        // Setup list listeners
        mEventList = (ListView) findViewById(R.id.eventListView);
        mEventList.setEmptyView(findViewById(R.id.eventListView_empty));
        mEventList.setOnItemClickListener(selectEventItemListener);
        mEventList.setOnScrollListener(listScrollListener);

        // Setup filter inputs
        mFilterGroupSpinner = (Spinner) findViewById(R.id.eventListGroupFilter);
        mFilterGroupSpinner.setOnItemSelectedListener(selectGroupSpinnerItem);

        mFilterName = (EditText) findViewById(R.id.filterName);
        mFilterName.addTextChangedListener(filterTextUpdated);
    }

    /**
     * Clears all observable data objects that are shown in the view. Currently used by demo to ensure that all lists are cleared on pause (since there is no demo logout)
     */
    private void clearView() {
        mListItemsArray = new ArrayList<iEvent>();
        mGroupItemsArray = new ArrayList<AlarmGroup>();
        mUserList = new UserList();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If we have no current login, then run in demo mode
        mService.setIsDemo(!LoginInfo.storedUserIsActive(this));

        // Attempt to get user list
        if (mUserList.isEmpty()) {
            mService.getUserList(new GenericCallback<UserList>() {
                @Override
                public void onCallback(UserList users) {
                	mUserList = users;                	                   
                }
            });
        }

        // Setup event list adapter and filter states
        setEventListAdapter();

        if (mService.getIsDemo()) {
            // Do not need to bind to anything. Sync with service demo data.
            syncWithDataFromService();
            updateStatusMessage();
        }
        else {
            // Bind to EventNotificationsService
            bindService(new Intent(this, EventNotificationsService.class), mConnection, Context.BIND_AUTO_CREATE);

            // Setup broadcast Receiver
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(EventNotificationsService.NOTIFICATION_INTENT_ACTION);
            registerReceiver(evReceiver, intentFilter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // If dialog is still open, dismiss it to prevent leaking issues.
        if (mDialog != null) {
            mDialog.dismiss();
        }

        if (mService.getIsDemo()) {
            // Do not need to unbind from anything; wipe the demo in case we are not coming back.
            clearView();
        }
        else {
            unregisterReceiver(evReceiver);
            unbindService(mConnection);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mListAdapter = null;
        mGroupAdapter = null;
    }

    /**
     * Save out state of filters so we can automatically re apply them if the orientation changes
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putInt(GROUP_SELECTED_POSITION, mGroupCurrentSelectedPos);
        savedInstanceState.putBoolean(AUTO_UPDATE, mAutoUpdate);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    // ------------------------------------------------------------------------------
    // Action Bar and menu
    // ------------------------------------------------------------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mRefreshMenuItem = menu.findItem(R.id.action_refresh);
        mAutoUpdateItem = menu.findItem(R.id.action_auto_refresh);
        mFilterItem = menu.findItem(R.id.action_filter);

        // If auto updating, menu icon should show pause button.
        // If not auto updating, manu icon should show play button.
        mAutoUpdateItem.setIcon((mAutoUpdate) ? R.drawable.ic_action_pause : R.drawable.ic_action_play);

        // Update menu icon to indicate if we have a filter applied or not.
        mFilterItem.setIcon(mFilterApplied ? R.drawable.ic_action_filter_selected : R.drawable.ic_action_filter);

        super.onPrepareOptionsMenu(menu);
        return true;
    }

    /**
     * @SuppressLint added since we are only running the API dependent function after we do a Build check.
     */
    @SuppressLint("NewApi")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                syncWithDataFromService();
                break;

            case R.id.action_auto_refresh:
                // If unpausing, do an immediate sync
                String message;
                if (mAutoUpdate) {
                    message = getString(R.string.paused);
                }
                else {
                    message = getString(R.string.unpaused);
                    syncWithDataFromService();
                }

                mAutoUpdate = !mAutoUpdate; // Toggle auto update field
                ShowCustomToast(App.getContext(), message, Toast.LENGTH_SHORT);                
                invalidateOptionsMenu();
                break;

            case R.id.action_filter:
                mEventListFilters.setVisibility(mEventListFilters.getVisibility() == View.GONE
                        ? View.VISIBLE
                        : View.GONE);
                break;

            case R.id.action_read_all:
                mService.readAllEvents();
                syncWithDataFromService();
                break;

            case R.id.action_unread_all:
                mService.unreadAllEvents();
                syncWithDataFromService();
                break;

            case R.id.action_dismiss_all:
                // Tell the service to dismiss events
                mService.dimissAllEvents();

                // Resync with (now empty) data
                syncWithDataFromService();
                break;

            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;

            case R.id.action_log_out:
                // Notify service
                mService.logout();

                // Route to settings page
                Intent settings = new Intent(App.getContext(), SettingsActivity.class);
                settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(settings);
                finish();
                this.overridePendingTransition(0, 0);

                break;

            default:
                break;
        }

        return true;

    }

    // ------------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------------
    TextWatcher filterTextUpdated = new TextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            applyFilters();
        }
    };

    /**
     * Attempts to update the event list with data cached by the notification service
     */
    private void syncWithDataFromService() {
        try {
            mListItemsArray = mService.getEventList();
            Log.i(App.TAG, String.format("Attempting to sync with service; loading %d events", mListItemsArray.size()));
            Collections.reverse(mListItemsArray);

            setGroupSpinnerAdapter();   // Update group filter dropdown
            setEventListAdapter();      // Update full event list
            updateNewEventsText();      // Show new events text on menu bar
        } 
        catch (Exception e) {
        	ShowCustomToast(MainActivity.this, getString(R.string.failed_to_sync_with_service), Toast.LENGTH_SHORT);            
        }
    }

    /**
     * Update menu title and icons based on mNotificationService.getNewEventCount
     */
    private void updateNewEventsText() {
        try {
            long newEvents = mService.getNewEventCount();
            if (newEvents == 0) {
                getActionBar().setTitle("");
                mRefreshMenuItem.setVisible(false);
            }
            else {
                String format = (newEvents == 1) ? getString(R.string.x_new_event) : getString(R.string.x_new_events);
                String text = String.format(format, mService.getNewEventCount());
                getActionBar().setTitle(text);
                mRefreshMenuItem.setVisible(true);
            }
        } 
        catch (Exception e) {
            Log.e(App.TAG, "Trouble in updateNewEventsText" + e.getMessage()); 
        }
    }

    // ------------------------------------------------------------------------------
    // Status bar functionality
    // ------------------------------------------------------------------------------
    /**
     * Update the message on the status bar
     */
    private void updateStatusMessage() {

        String message = "";
        boolean ok = true;

        if (mService == null) {
            message = getString(R.string.serverstatus_waiting_for_response);
            ok = false;
        }
        else if (mService.getIsDemo()) {
            message = getString(R.string.serverstatus_demo_mode);
        }
        else {
            EventNotificationsService.STATUS status = mService.getCurrentStatus();
            Date lastSuccess = mService.getLastSuccess();
            String lastSuccessStr = (lastSuccess == null)
                    ? ""
                    : String.format(getString(R.string.serverstatus_last_success_at_X), lastSuccess.toString());

            // If unknown then we may just be starting up the service for the first time
            if (status.equals(EventNotificationsService.STATUS.UNKNOWN)) {
                if (mListItemsArray.size() > 0) {
                    // We have loaded from cache; we have connected to the service
                    message = getString(R.string.serverstatus_connected_to_service);
                }
                else {
                    message = getString(R.string.serverstatus_waiting_for_response);
                }

                ok = true;
            }
            else if (status.equals(EventNotificationsService.STATUS.OK)) {
                message = getString(R.string.serverstatus_connected_to_service);
                ok = true;
            }
            else if (status.equals(EventNotificationsService.STATUS.INVALID_LOGIN)) {
                message = getString(R.string.serverstatus_invalid_login_credentials);
                ok = false;
            }
            else if (status.equals(EventNotificationsService.STATUS.NOT_CONNECTED)){
                message = getString(R.string.serverstatus_not_connected);
                ok = false;
            }
            else {
                // STATUS.NETWORK_ERROR etc...
                message = getString(R.string.serverstatus_problems_communicating_with_server) + " " + lastSuccessStr;
                ok = false;
            }

        }

        mStatusMessage.setText(message);
        mStatusEwebConnection.setImageResource(ok ? R.drawable.ic_check_ok : R.drawable.ic_question);
    }

    // ------------------------------------------------------------------------------
    // Group Spinner adapter functionality
    // ------------------------------------------------------------------------------
    /**
     * Updates the alarm group filter spinner
     */
    private void setGroupSpinnerAdapter() {
        mGroupItemsArray.clear();

        // Create all categories in drop down
        if (mService.getAlarmGroupInfo().size() > 0) {
            mGroupItemsArray.add(new AlarmGroup(mGroupNameForAllEvents, "#00000000", null));
            mGroupItemsArray.addAll(mService.getAlarmGroupInfo().values());
        }

        mGroupAdapter = new GroupSpinnerItemAdapter(mCtx, R.layout.layout_group_spinner_item, mGroupItemsArray);
        mFilterGroupSpinner.setAdapter(mGroupAdapter);
        mFilterGroupSpinner.setSelection(mGroupCurrentSelectedPos);
    }

    /**
     * Listener for alarm group spinner selection; automatically applies filter to the list
     */
    private OnItemSelectedListener selectGroupSpinnerItem = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
            mGroupCurrentSelectedPos = position;
            applyFilters();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parentView) {
            mGroupCurrentSelectedPos = 0;
            applyFilters();
        }
    };

    /**
     * Reads the values supplied by the user in the filter view, creates the corresponding JSONObject to store those filters and 
     * then applies the filters to the list. Note: See
     * {@link EventItemAdapter.FilterBy} for the acceptable values for the filter JSONObject.
     */
    private void applyFilters() {
        if (mListAdapter == null) {
            return;
        }

        // Generate string containing complex filter JSON
        mFilterApplied = false;
        JSONObject filters = new JSONObject();

        try {
            // Alarm Group
            Object o = mFilterGroupSpinner.getItemAtPosition(mGroupCurrentSelectedPos);
            if (o instanceof AlarmGroup) {
                AlarmGroup group = (AlarmGroup) o;

                if (group.name.equals(mGroupNameForAllEvents)) {
                    filters.put("groupName", null);
                }
                else {
                    filters.put("groupName", group.name);
                    mFilterApplied = true;
                }
            }

            // Strings, only add if not empty
            String text;
            text = mFilterName.getText().toString();
            if (!text.isEmpty()) {
                filters.put("text", text);
                mFilterApplied = true;
            }
        } 
        catch (Exception e) {
            Log.e(App.TAG, "Problem creating filter JSON: " + e.getMessage());
        }

        invalidateOptionsMenu();
        mListAdapter.getFilter().filter(filters.toString());
    }

    // ------------------------------------------------------------------------------
    // Event List adapter functionality
    // ------------------------------------------------------------------------------
    /**
     * (Re)sets the list adapter with the items in listItemsArray
     */
    private void setEventListAdapter() {
        // Setup custom list adapter to show stats from eweb.

        if (mListAdapter == null) {
            mListAdapter = new EventItemAdapter(mCtx, R.layout.layout_event_item, mListItemsArray);
            mEventList.setAdapter(mListAdapter);
        }
        else {
            mListAdapter.updateData(mListItemsArray);
        }

        // (Re)Apply filters
        applyFilters();
    }

    /**
     * Allows us to check when to show up and down arrows above and below the list view
     */
    private OnScrollListener listScrollListener = new OnScrollListener() {
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            int lastItem = firstVisibleItem + visibleItemCount;

            // Top scroll indicator
            if (firstVisibleItem == 0) {
                // hide top
                if (isHeaderVisible) {
                    mEventListFauxHeader.setVisibility(View.GONE);
                    isHeaderVisible = false;
                }
            }
            else if (!isHeaderVisible) {
                // show top
                mEventListFauxHeader.setVisibility(View.VISIBLE);
                isHeaderVisible = true;
            }

            if (lastItem == totalItemCount) {
                if (isFooterVisible) {
                    mEventListFauxFooter.setVisibility(View.GONE);
                    isFooterVisible = false;
                }
            }
            else if (!isFooterVisible) {
                mEventListFauxFooter.setVisibility(View.VISIBLE);
                isFooterVisible = true;
            }
        }
    };

    private OnClickListener openEWEBPage(final String link) {

        LoginInfo login = LoginInfo.getLoginInfo(mCtx);
        final String openUrl = String.format("%s%s", login.url, link);

        return new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(openUrl));
                    startActivity(intent);
                } 
                catch (Exception e) {
                    // Failed to launch activity, perhaps due to bad url?                    
                    ShowCustomToast(mCtx, getString(R.string.error_launching_eweb_link), Toast.LENGTH_LONG);
                }
            }
        };
    }

    /**
     * When event item is clicked in the list, pop open a detailed dialog for the item which allows the user to interact 
     */
    private OnItemClickListener selectEventItemListener = new OnItemClickListener() {
        /*
         * When item clicked, pop up dialog to show event details 
         */
        public void onItemClick(AdapterView<?> parent, final View view, final int position, long id) {
            Object o = mEventList.getItemAtPosition(position);

            if (o instanceof iEvent) {
                mSelectedEvent = (iEvent) o;

                // Set the event as being "read" and sync back to cache
                mSelectedEvent.hasBeenViewed = true;
                mService.updateEventInCache(mSelectedEvent);

                // Update list item since it is no longer "unread"
                mListAdapter.getView(position, view, mEventList);

                // Use layout_event_item as a base (what the list view uses)
                mDialog = new Dialog(mCtx);
                mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                mDialog.getWindow().setBackgroundDrawableResource(R.drawable.shape_event_item_background); // Rounds corners
                mDialog.setContentView(R.layout.layout_event_item_details_full);

                // Set outlets
                LinearLayout detailsLayout = (LinearLayout) mDialog.findViewById(R.id.eventDetailsLayout);
                final View ackLayout = mDialog.findViewById(R.id.ackLayout);
                final TextView ackMessage = (TextView) mDialog.findViewById(R.id.ackMessage);
                final View assignToLayout = mDialog.findViewById(R.id.assignToLayout);
                final Spinner userSpinner = (Spinner) mDialog.findViewById(R.id.eventAssignTo);
                final Button dialogButtonAck = (Button) mDialog.findViewById(R.id.dialogButtonACK);
                final TextView eventNotes = (TextView) mDialog.findViewById(R.id.eventNotes);
                final Button dialogButtonSave = (Button) mDialog.findViewById(R.id.dialogButtonSave);

                ((TextView) mDialog.findViewById(R.id.eventName)).setText(mSelectedEvent.getEventName());
                ((TextView) mDialog.findViewById(R.id.inputName)).setText(mSelectedEvent.getInputName());
                ((TextView) mDialog.findViewById(R.id.eventTimestamp)).setText(mSelectedEvent.getEventTimestamp());
                ((TextView) mDialog.findViewById(R.id.eventMessage)).setText(mSelectedEvent.getMessage());
                ((ImageView) mDialog.findViewById(R.id.toStateIcon)).setImageResource(App.getToStateIcon(mSelectedEvent));

                // Change background DRAWABLE colour so that we preserve any other styling (ie. corner radius)
                RelativeLayout categoryColorLayout = (RelativeLayout) mDialog.findViewById(R.id.categoryColorLayout);
                GradientDrawable categoryColor = (GradientDrawable) categoryColorLayout.getBackground();
                categoryColor.setColor(mSelectedEvent.getCategoryColor(getResources().getColor(R.color.defaultCategoryColor)));

                // Add details
                detailsLayout.removeAllViews();
                List<List<String>> linkedDetails = new ArrayList<List<String>>();
                List<List<String>> eventDetails = new ArrayList<List<String>>();
                String label, link, linkText, encodedText;
                EventDetailView detail;

                String eventLink = mSelectedEvent.getEventLink();
                String inputLink = mSelectedEvent.getInputLink();

                // Demo = no links
                if (mService.getIsDemo()) {
                    eventDetails.add(Arrays.asList(getString(R.string.event), mSelectedEvent.getEventName()));
                    eventDetails.add(Arrays.asList(getString(R.string.input), mSelectedEvent.getInputName()));

                }
                // Else, attempt to add links for the objects
                else {
                    // If link is given in the JSON, then use it
                    if (eventLink == null || eventLink.isEmpty()) {
                        eventDetails.add(Arrays.asList(getString(R.string.event), mSelectedEvent.getEventName()));
                    }
                    else {
                        linkedDetails.add(Arrays.asList(getString(R.string.event), eventLink, mSelectedEvent.getEventName()));
                    }

                    if (inputLink == null || inputLink.isEmpty()) {
                        eventDetails.add(Arrays.asList(getString(R.string.input), mSelectedEvent.getInputName()));
                    }
                    else {
                        linkedDetails.add(Arrays.asList(getString(R.string.input), inputLink, mSelectedEvent.getInputName()));
                    }

                    // Create the correct HTML for the item
                    for (int i = 0; i < linkedDetails.size(); i++) {
                        label = linkedDetails.get(i).get(0);
                        link = linkedDetails.get(i).get(1);
                        linkText = linkedDetails.get(i).get(2);

                        encodedText = TextUtils.htmlEncode(linkText);
                        detail = new EventDetailView(App.getContext(), R.layout.view_event_detail_link);
                        detail.setHTML(label, "<u>" + encodedText + "</u>", openEWEBPage(link));
                        int pad = App.dpToPx(10);
                        detail.setPadding(0, 0, 0, pad);
                        detailsLayout.addView(detail);
                    }
                }

                // Add non-linked details
                eventDetails.add(Arrays.asList(getString(R.string.eventdetail_event_ref), mSelectedEvent.getEventRef()));
                eventDetails.add(Arrays.asList(getString(R.string.eventdetail_input_ref), mSelectedEvent.getInputRef()));
                eventDetails.add(Arrays.asList(getString(R.string.eventdetail_alarm_group), mSelectedEvent.getAlarmGroupName()));
                eventDetails.add(Arrays.asList(getString(R.string.eventdetail_alarm_priority), mSelectedEvent.getPriority()));
                eventDetails.add(Arrays.asList(getString(R.string.eventdetail_raw_timestamp), mSelectedEvent.getRawTimestamp()));
                eventDetails.add(Arrays.asList(getString(R.string.eventdetail_from_state), mSelectedEvent.getFromState()));
                eventDetails.add(Arrays.asList(getString(R.string.eventdetail_to_state), mSelectedEvent.getToState()));

                for (int i = 0; i < eventDetails.size(); i++) {
                    detail = new EventDetailView(App.getContext(), R.layout.view_event_detail);
                    detail.set(eventDetails.get(i).get(0), eventDetails.get(i).get(1));
                    detailsLayout.addView(detail);
                }

                // Show/hide ack layout if required
                if (mSelectedEvent.ackRequired()) {
                    ackLayout.setVisibility(View.VISIBLE);
                }

                // Setup Close button
                Button dialogButtonClose = (Button) mDialog.findViewById(R.id.dialogButtonClose);
                dialogButtonClose.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mDialog.dismiss();
                    }
                });

                // Setup user list
                mDialogUserListAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, mUserList);
                mDialogUserListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                userSpinner.setAdapter(mDialogUserListAdapter);

                // Attempt to get user list
                if (mUserList.isEmpty()) {
                    // Catches case where user list failed to load when the main list was loaded (say, due to no network connection at the time)
                    // If user list is empty, then attempt to get the list and update the UI accordingly
                    // Note, getAlarmDetails also updates the userSpinner, so the code is very similar in both cases. If changing one,
                    // make sure to change the other.
                    mService.getUserList(new GenericCallback<UserList>() {
                        @Override
                        public void onCallback(UserList users) {
                            mUserList = users;                           

                            // Set back up again
                            mDialogUserListAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, mUserList);
                            mDialogUserListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            userSpinner.setAdapter(mDialogUserListAdapter);

                            // Update layout
                            if (mDialogUserListAdapter.getCount() > 0) {
                                int startPos = mDialogUserListAdapter.getPosition(mSelectedEvent.AlarmDetails.getAssignee());
                                userSpinner.setSelection(startPos);
                                assignToLayout.setVisibility(View.VISIBLE);
                                userSpinner.setEnabled(true);
                            }
                            else {
                                assignToLayout.setVisibility(View.GONE);
                            }
                        }
                    });
                }

                // Setup ACK button
                dialogButtonAck.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialogButtonAck.setEnabled(false); // Disable button

                        mService.acknowledgeEvent(mSelectedEvent, ackMessage.getText().toString(), new GenericCallback<FetchXML.Result>() {
                            @Override
                            public void onCallback(Result result) {
                                if (result.success) {
                                	ShowCustomToast(mCtx, getString(R.string.transition_acknowledged), Toast.LENGTH_LONG);                                    

                                    // Mark the selected event as being ack'd, then update the service cache since we only have a copy.
                                    mSelectedEvent.setAsAcknowledged();
                                    mService.updateEventInCache(mSelectedEvent);
                                    mListAdapter.updateData(mListItemsArray);
                                    applyFilters(); // Re-apply current filters.

                                    mDialog.dismiss(); // Close dialog
                                    mDialog = null;
                                }
                                else {
                                    dialogButtonAck.setEnabled(true);
                                    ShowCustomToast(mCtx, getString(R.string.error_acknowledging_event), Toast.LENGTH_LONG);                                    
                                    Log.e(App.TAG, "Error ack'ing event: " + result.rawResponse);
                                }
                            }
                        });
                    }
                });

                // Setup notes field
                // Show/hide ack button/flag if required
                mService.getAlarmDetails(mSelectedEvent, new GenericCallback<iEvent.iAlarmDetails>() {
                    @Override
                    public void onCallback(iAlarmDetails details) {

                        if (details == null) {
                            // Trouble contacting the server; update UI to disable all unavailable
                            userSpinner.setEnabled(false);
                            eventNotes.setEnabled(false);
                            ackMessage.setEnabled(false);
                            dialogButtonSave.setEnabled(false);
                            dialogButtonAck.setEnabled(false);

                            String error = getString(R.string.error_retrieving_event_details);
                            Log.e(App.TAG, error);
                            ShowCustomToast(MainActivity.this, error, Toast.LENGTH_LONG);                            
                        }
                        else {
                            // Update AlarmDetails for event
                            mSelectedEvent.AlarmDetails.setAssignee(details.getAssignee());
                            mSelectedEvent.AlarmDetails.setText(details.getText());

                            // Set assignee
                            if (mDialogUserListAdapter.getCount() > 0) {
                                int startPos = mDialogUserListAdapter.getPosition(mSelectedEvent.AlarmDetails.getAssignee());
                                userSpinner.setSelection(startPos);
                                assignToLayout.setVisibility(View.VISIBLE);
                            }
                            else {
                                assignToLayout.setVisibility(View.GONE);
                            }

                            // Set notes
                            eventNotes.setText(mSelectedEvent.AlarmDetails.getText());

                            // Make layouts and buttons visible
                            userSpinner.setEnabled(true);
                            eventNotes.setEnabled(true);
                            ackMessage.setEnabled(true);
                            dialogButtonSave.setEnabled(true);
                            dialogButtonAck.setEnabled(true);
                        }
                    }
                });

                // Setup Save button
                dialogButtonSave.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Save!
                        dialogButtonSave.setEnabled(false);

                        final String user = userSpinner.getSelectedItem().toString();
                        mSelectedEvent.AlarmDetails.setAssignee(user);
                        // Note, use empty user name to remove assign-to

                        String newNote = eventNotes.getText().toString();
                        if (!newNote.equals(mSelectedEvent.AlarmDetails.getText())) {
                            mSelectedEvent.AlarmDetails.setText(newNote);
                        }

                        GenericCallback<FetchXML.Result> updateAlarmDetailsListener = new GenericCallback<FetchXML.Result>() {
                            @Override
                            public void onCallback(Result result) {
                                String status = "OK";

                                if (result.success) {
                                	ShowCustomToast(mCtx, getString(R.string.alarm_detail_saved), Toast.LENGTH_LONG); 
                                    if (mDialog != null) { mDialog.dismiss(); }
                                    mDialog = null;
                                    mService.updateEventInCache(mSelectedEvent);
                                }
                                else {
                                    String error = getString(R.string.error_saving_event_details) + " " + result.rawResponse;
                                    Log.i(App.TAG, error);
                                    ShowCustomToast(mCtx, error, Toast.LENGTH_LONG);                                    
                                }
                                
                                dialogButtonSave.setEnabled(true);
                            }
                        };

                        mService.setAlarmDetails(mSelectedEvent, updateAlarmDetailsListener);
                    }
                });

                mDialog.show();
            }
        }
    };

	public void ShowCustomToast(Context context, String textMessage, int duration) {
    	Toast cusToast = Toast.makeText(context, textMessage, duration);
    	
    	cusToast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
    	cusToast.show();	    	
	}    
}
