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
package com.deltacontrols.eventviewer.service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.deltacontrols.eventviewer.App;
import com.deltacontrols.eventviewer.LoginInfo;
import com.deltacontrols.eventviewer.MainActivity;
import com.deltacontrols.eventviewer.R;
import com.deltacontrols.eweb.support.api.EwebConnection;
import com.deltacontrols.eweb.support.api.FetchJSON;
import com.deltacontrols.eweb.support.interfaces.GenericCallback;
import com.deltacontrols.eweb.support.models.AlarmGroup;
import com.deltacontrols.eweb.support.models.iEvent;
import com.deltacontrols.eweb.support.models.iEventList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


/**
 * Provides main communication with eWEB to get event notifications. Currently using basic HTTP auth to make requests, sessions are not kept.
 */
public class EventNotificationsService extends Service {

    // ------------------------------------------------------------------------------
    // Private classes
    // ------------------------------------------------------------------------------
    /**
     * If you want the service to stop while the device is turned off (for battery savings)
     * Uncomment the stopServiceRepeating and startServiceRepeating lines that are commented
     * out below.
     */
    private class CustomBroadcastReciever extends BroadcastReceiver {

        private boolean mIsScreenOn;

        @SuppressWarnings("unused")
        public boolean isScreenOn() {
            return mIsScreenOn;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                Log.i(App.TAG, "CustomBroadcastReciever ACTION_SCREEN_OFF");
                mIsScreenOn = false;
                // ScheduleEventNotifications.stopServiceRepeating(EventNotificationsService.this);
            }
            else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                Log.i(App.TAG, "CustomBroadcastReciever ACTION_SCREEN_ON");
                mIsScreenOn = true;
                // ScheduleEventNotifications.startServiceRepeating(EventNotificationsService.this);
            }
        }
    };

    private CustomBroadcastReciever mScreenReceiver; // Setup notification receiver from device

    // ------------------------------------------------------------------------------
    // Static properties
    // ------------------------------------------------------------------------------
    private final static String CACHED_FILENAME = "cachedList.json";
    public final int NOTIFICATION_ICON_ID = 0;
    public final int NOTIFICATION_MESSAGE_ID = 0;
    public final static String NOTIFICATION_INTENT_ACTION = "NewEvents";

    public static enum STATUS {
        UNKNOWN, OK,
        INVALID_LOGIN,
        NETWORK_ERROR,
        EWEB_ERROR,
        NOT_CONNECTED
    };

    public static enum AlertLevel {
        HIGH,
        MEDIUM,
        LOW
    };

    public static int LongerReadTimeout = 9000 * 10; 
    
    // ------------------------------------------------------------------------------
    // Private properties
    // ------------------------------------------------------------------------------
    private static String UnknownIndex = "0";
    private final IBinder mBinder = new MyBinder();
    private Intent mNotificationIntent = new Intent();

    // ------------------------------------------------------------------------------
    // Read-only properties
    // ------------------------------------------------------------------------------
    private boolean mIsFetching = false;

    public boolean getIsFetching() {
        return mIsFetching;
    }

    /**
     * The list of all new events for the last request; because we may need to make multiple requests to 
     * fulfill a request, this is required to hold temporary results.
     */
    private ArrayList<iEvent> mNewEventsList;

    /**
     * Tracks the number of 'new' events. Clients (EventViewer) may choose to clear this number at will
     */
    private int mNewEventCount = 0;

    public int getNewEventCount() {
        return mNewEventCount;
    }

    public void resetNewEventCount() {
        mNewEventCount = 0;
    }

    /**
     * Index of that last/latest event received from eWEB Can be used to make requests to 
     * eWEB to avoid large DB queries
     */
    private String mLastIndex = UnknownIndex;

    public String getLastKnownIndex() {
        return mLastIndex;
    }

    /**
     * Date of last successful response from eWEB
     */
    private Date mLastSuccess = null;

    public Date getLastSuccess() {
        return (mLastSuccess == null) ? null : (Date) mLastSuccess.clone();
    }

    /**
     * Main array of cached events Should never have size > EVENT_CACHE_MAX
     */
    private EventCache mEventCache = new EventCache(EventCache.EVENT_CACHE_MAX);

    public HashMap<String, AlarmGroup> getEventCacheAlarmGroupInfo() {
        return mEventCache.alarmGroupInfo;
    }

    public ArrayList<iEvent> getEventCacheCopy() {
        return mEventCache.getCopy();
    }

    /**
     * Update event both in active and stored (file) cache
     */
    public void updateEventInCache(iEvent ev) {
        mEventCache.updateEvent(ev);
        writeToCacheFile(EventNotificationsService.this);
    }

    /**
     * Get current status of service Allows clients to visually indicate to users that the 
     * service may not be running correctly
     */
    private STATUS mCurrentStatus = STATUS.UNKNOWN;

    public STATUS getCurrentStatus() {
        return mCurrentStatus;
    }

    /**
     * Send out a broadcast indicating that a notification has been received.
     */
    private void sendUpdateBroadcast() {
        Log.i(App.TAG, String.format("SERVICE (sendUpdateBroadcast)"));
        // Inform any activities listening
        mNotificationIntent = new Intent();
        mNotificationIntent.setAction(NOTIFICATION_INTENT_ACTION);
        sendBroadcast(mNotificationIntent);
    }

    // ------------------------------------------------------------------------------
    // Service Binder
    // ------------------------------------------------------------------------------
    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(App.TAG, "SERVICE onBind");
        return mBinder;
    }

    public class MyBinder extends Binder {
        public EventNotificationsService getService() {
            Log.i(App.TAG, "SERVICE MyBinder getService");
            return EventNotificationsService.this;
        }
    }

    // ------------------------------------------------------------------------------
    // Life Cycle
    // ------------------------------------------------------------------------------
    @Override
    public void onCreate() {
        Log.i(App.TAG, "SERVICE onCreate");
        mScreenReceiver = new CustomBroadcastReciever();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter);

        // Attempt to load from cache and set last known index based on values in cache
        loadFromCacheFile(this);
        mLastIndex = mEventCache.getLastKnownIndex();
        if (mLastIndex == null) {
            mLastIndex = UnknownIndex;
        }
    }

    /**
     * This is deprecated, but you have to implement it if you're planning on supporting devices 
     * with an API level lower than 5 (Android 2.0).
     */
    @Override
    public void onStart(Intent intent, int startId) {
        Log.i(App.TAG, "SERVICE onStart");
        handleIntent(intent);
    }

    /**
     * This is called on 2.0+ (API level 5 or higher). Returning START_NOT_STICKY tells the system to not restart the 
     * service if it is killed because of poor resource (memory/cpu) conditions.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(App.TAG, "SERVICE onStartCommand");
        handleIntent(intent);
        return START_STICKY;
    }

    /**
     * In onDestroy() we release our wake lock. This ensures that whenever the Service stops (killed for resources, 
     * stopSelf() called, etc.), the wake lock will be released.
     */
    public void onDestroy() {
        Log.i(App.TAG, "SERVICE onDestroy");
        super.onDestroy();

        unregisterReceiver(mScreenReceiver);
        // mWakeLock.release();
    }

    // ------------------------------------------------------------------------------
    // Main service implementation
    // ------------------------------------------------------------------------------
    /**
     * This is where we initialize. We call this when onStart/onStartCommand is called by the system. We won't do anything 
     * with the intent here, and you probably won't, either.
     */
    private void handleIntent(Intent intent) {
        Log.i(App.TAG, "SERVICE handleIntent");
        doWork();
    }

    /**
     * Do the main service work here, contact eWEB if we are logged in etc.
     */
    private void doWork() {
        // Crude way to avoid having two requests out at once (handles case where eWEB takes longer to respond then
        // the repeat time. We do not want to miss any alarms, thus we do not want to double up on requests (since we are
        // currently passing along lastIndex).
        if (mIsFetching) {
            return;
        }

        final EwebConnection eWeb = App.getEwebConnection();
        mNewEventsList = new ArrayList<iEvent>(); // Reset new events list.

        Log.i(App.TAG, String.format("SERVICE (doWork): Starting"));
        LoginInfo login = LoginInfo.getLoginInfo(this);

        // Check to make sure we have enough information to run the service.
        // If not, we must wait for the client (EventViewer) to setup the login information and then restart the service repeating.
        if (!LoginInfo.storedLoginExists(this) || !LoginInfo.storedUserIsActive(this)) {
            logout();
            Log.i(App.TAG, "SERVICE (doWork) : Stored user no longer exists, stopping service");
            return;
        }

        // Check if login has occur
        if (eWeb.getConnectionStatus() == EwebConnection.CONNECTION_STATUS.NOT_INITIALIZED){
            // Call without caring for the response. Result will check later.
            eWeb.connect(login.url, login.username, login.password, null, login.mBasicAuthentication);
            ScheduleEventNotifications.stopServiceRepeating(this);
            ScheduleEventNotifications.startServiceRepeating(this, 0, login.refreshSeconds);
            return;
        };

        // if eWeb is not connected, inform user
        if (!eWeb.isConnected()){

            String title = getString(R.string.notification_event_viewer_failed_to_update);
            String message = getString(R.string.serverstatus_not_connected);
            mCurrentStatus = STATUS.NOT_CONNECTED;
            createNotification(title, message, "", 0);
            sendUpdateBroadcast();
            return;
        }

        mIsFetching = true;
        String lastIndex = "";
        // Used in sequence-le to attempt to speed up response from eWEB. Use sql unsigned big int as max value.
        String maxSQLIndex = "9223372036854775807"; 

        // If we do not have a mLastIndex
        if (mLastIndex.equals(UnknownIndex)) {
            // Attempt to use the user's dismissIndex
            if (login.dismissIndex != null) {
                lastIndex = "startID=-" + String.valueOf(login.dismissIndex);
            }
            else {
                // Else, give the api a max value and ask to get all records BEFORE that.
                // Note: eWEB seems to run faster when given an index vs. just asking for "the most recent".
                lastIndex = "sequence-le=" + maxSQLIndex;
            }
        }
        else {
            // Use mLastIndex as it is the most recent value
            lastIndex = "startID=-" + String.valueOf(mLastIndex);
        }

        // No need to request more than the max cache.
        String maxResults = "max-results=" + EventCache.EVENT_CACHE_MAX;

        eWeb.getEventList(lastIndex, maxResults, mHandleResultCallback, LongerReadTimeout);
        Log.i(App.TAG, String.format("SERVICE (doWork): Starting request (mLastIndex: %s)", mLastIndex.toString()));
    }
    
    /**
     * Handler for data result. Runs on a background thread to avoid locking up the main UI.
     */
    private GenericCallback<FetchJSON.Result> mHandleResultCallback = new GenericCallback<FetchJSON.Result>() {
        @Override
        public void onCallback(final FetchJSON.Result fetchResult) {
            // Run on different thread so we don't hang the UI if it is running.
            Thread thread = new Thread() {
                @Override
                public void run() {
                    mHandleResult(fetchResult);
                }
            };
            thread.start();
        }
    };

    /**
     * Handles the JSON result from eWEB; checks for errors and then processes the data, updates the cache and sends out a broadcast if new data has been received.
     */
    private void mHandleResult(FetchJSON.Result fetchResult) {

        String title, message;
        JSONObject result = fetchResult.json;

        // Parse result
        iEventList lastGet = iEventList.fromJson(result);

        if (lastGet.events == null) {
            Log.i(App.TAG, "Service - handleJSONResult data is null, do nothing.");
            mIsFetching = false;
            return;
        }

        try {
            // Check for failed response:
            // - the request itself failed
            // - or the response contained a success JSON object that indicated a failure
            boolean failed = (!fetchResult.success) || ((result.has("success") && !result.getBoolean("success")));
            if (failed) {
                title = getString(R.string.notification_event_viewer_failed_to_update);

                if (fetchResult.statusCode == HttpStatus.SC_UNAUTHORIZED) {
                    mCurrentStatus = STATUS.INVALID_LOGIN;
                    message = getString(R.string.notification_invalid_eweb_login);
                }
                else {
                    // Assume network connection issue
                    mCurrentStatus = STATUS.EWEB_ERROR;
                    message = getString(R.string.notification_network_connection_issue);
                }

                createNotification(title, message, "", 0);
                sendUpdateBroadcast();
                Log.i(App.TAG, String.format("SERVICE (handleJSONResult): %s", message));

                mIsFetching = false;
                return;
            }

            // Else we have a successful result.

            // Add events from last get
            if (lastGet.events.size() > 0) {
                // iEventList sorts the events in ASC order on event index number.
                // This means that we need to place the event list at the beginning of the FULL list (mNewEventsList)
                // to make sure the ASC order is maintained.
                mNewEventsList.addAll(0, lastGet.events);
            }

            // Check if we have MORE data to get
            // Note, it appears that eWEB is returning next url even when 0 events remain
            // This means we cannot rely on the existence of the next url to tell when we are "done" getting data.
            // Instead use the number of results we have retrieved.
            // Also note: this is our original request if our cache is empty. Only use "next" if this is not the case.
            if ((mEventCache.size() != 0)
                    && (lastGet.next != null)
                    && (mNewEventsList.size() <= EventCache.EVENT_CACHE_MAX)) {
                Log.i(App.TAG, String.format("SERVICE (handleJSONResult): Next URL found, %s", lastGet.next));
                getNextData(lastGet.next, EventCache.EVENT_CACHE_MAX - mNewEventsList.size());
                // Note, still fetching!
                return;
            }

            // When here, we have all the data we need to respond to the request.

            // Determine the latest/largest ID returned with this data set.
            // Note: lastGet.data.index no longer appears to return the highest index; it returns the first? or 0?
            // If no new data returned, then assume matching max index until we
            // know that we can depend on lastGet.data.index
            String lastGetLastIndex = mLastIndex;
            int numEvents = mNewEventsList.size();

            if (numEvents > 0) {
                lastGetLastIndex = mNewEventsList.get(numEvents - 1).getIndex();
            }

            mLastSuccess = Calendar.getInstance().getTime();

            // We have new data!
            if (!mLastIndex.equals(lastGetLastIndex)) {
                mCurrentStatus = STATUS.OK;

                // Update last index
                mLastIndex = lastGetLastIndex;
                mNewEventCount = mNewEventCount + mNewEventsList.size();

                // Add to stored cache
                mEventCache.addAll(mNewEventsList);
                writeToCacheFile(EventNotificationsService.this);

                // Create android system notification
                updateNewEventsNotification();

                // Always send an update broadcast when new data available
                sendUpdateBroadcast();

                Log.i(App.TAG, String.format("SERVICE (handleJSONResult): %s Total events, %s New events", mEventCache.size(), mNewEventCount));
            }
            // No new data.
            else {
                // Only send broadcast if we transition from a non-ok status to an ok status to allow client UIs to be updated
                if (mCurrentStatus != STATUS.OK) {
                    mCurrentStatus = STATUS.OK;
                    updateNewEventsNotification();
                    sendUpdateBroadcast();
                }

                Log.i(App.TAG, "SERVICE (handleJSONResult): No new events");
            }
        } 
        catch (Exception e) {
            mCurrentStatus = STATUS.EWEB_ERROR;

            title = getString(R.string.notification_event_viewer_failed_to_update);
            message = getString(R.string.notification_error_getting_events);
            createNotification(title, message, "", 0);

            Log.e(App.TAG, String.format("SERVICE (handleJSONResult): Error found: %s", e.getMessage()));
        }

        // Attempt to run the next item in the queue.
        mIsFetching = false;
    }
    
    /**
     * If another request is required to fulfill the original request, eWEB will return a "next" url.
     * This method runs that url and then calls the original callback.
     * @param url
     * @param maxResults
     */
    private void getNextData(String url, int maxResults) {
        String fullURL = String.format("%s&alt=json", url);
        EwebConnection eWeb = App.getEwebConnection();

        eWeb.getNextEventList(fullURL, mHandleResultCallback, LongerReadTimeout);

        Log.i(App.TAG, String.format("SERVICE (getNextData): Starting request (fullURL: %s)", fullURL));
    }

    // ------------------------------------------------------------------------------
    // Private Helper Functions
    // ------------------------------------------------------------------------------
    /**
     * Creates a system notification based on the current mNewEventCount. If there are no new events then the notification is cleared as there is nothing to report.
     */
    private void updateNewEventsNotification() {

        if (mNewEventCount == 0) {
            clearSystemNotification();
        }
        else {
            // Create android system notification
            String format = (mNewEventCount == 1) ? getString(R.string.x_new_event) : getString(R.string.x_new_events);
            String title = String.format(format, mNewEventCount);
            String message = getString(R.string.notification_touch_to_view);
            String content = "";
            createNotification(title, message, content, mNewEventCount);
        }
    }

    /**
     * Creates a system notification containing the desired message and content 
     * 
     * @SuppressWarnings("deprecation") Remove if app moves to use .build (API 16+)
     */
    @SuppressWarnings("deprecation")
    private void createNotification(String title, String message, String content, int num) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);

        Notification n = new Notification.Builder(this)
                .setContentTitle(title).setContentText(message)
                .setContentInfo(content)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pIntent)
                .setNumber(num)
                .setTicker(title)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setAutoCancel(true).getNotification(); // use .build if API 16+

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_MESSAGE_ID, n);
    }

    // ------------------------------------------------------------------------------------------------
    // Serializing JSON to/from internal file so that data can persist if app process is killed and restarted
    // ------------------------------------------------------------------------------------------------
    /**
     * Writes eventCache json object to a file in internal storage so that it may persist if application is killed and restarted. 
     * Run on different thread so we don't hang the UI
     */
    private void writeToCacheFile(Context ctx) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                ArrayList<iEvent> list = mEventCache.getCopy();
                Gson gson = new Gson();
                String listJSON = gson.toJson(list);
                FileOutputStream outputStream;

                try {
                    outputStream = App.getContext().openFileOutput(CACHED_FILENAME, Context.MODE_PRIVATE);
                    outputStream.write(listJSON.getBytes());
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    /**
     * Note, for now this file blocks the UI thread as we need to get the data
     * before we can proceed to show the list and do updates correctly
     */
    private void loadFromCacheFile(Context ctx) {
        Gson gson = new Gson();

        try {
            FileInputStream in = ctx.openFileInput(CACHED_FILENAME);
            InputStreamReader inputStreamReader = new InputStreamReader(in);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }

            inputStreamReader.close();
            Type listType = new TypeToken<List<iEvent>>() {
            }.getType(); // Wowza! Ugly but that's Java for you.
            ArrayList<iEvent> list = gson.fromJson(sb.toString(), listType);

            mEventCache.addAll(list);
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteCacheFile() {
        deleteFile(CACHED_FILENAME);
    }

    // ------------------------------------------------------------------------------
    // Helper Functions
    // Exposed to clients by binding to the service
    // ------------------------------------------------------------------------------
    public void clearCache() {
        mEventCache.clear();
        mNewEventCount = 0;
        deleteCacheFile();
    }

    /**
     * Removes any existing notifications from the system notification window.
     */
    public void clearSystemNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_MESSAGE_ID);
    }

    public void markAllAsRead() {
        for (iEvent event : mEventCache) {
            event.hasBeenViewed = true;
        }
        writeToCacheFile(EventNotificationsService.this);
    }

    public void markAllAsUnread() {
        for (iEvent event : mEventCache) {
            event.hasBeenViewed = false;
        }
        writeToCacheFile(EventNotificationsService.this);
    }

    /**
     * Resets all values associated with a service "session" Note: Does not actually contact eWEB at all to log out of any 
     * existing eWEB sessions (although currently we are not using one)
     */
    public void logout() {
        Log.i(App.TAG, "Service logout");

        mLastIndex = UnknownIndex;
        mCurrentStatus = STATUS.UNKNOWN;

        mEventCache.clear();
        mNewEventCount = 0;
        mLastSuccess = null;
        mIsFetching = false;

        // Notifications no longer relevant
        clearSystemNotification();

        // Clear any files in internal storage
        deleteCacheFile();

        // Stop any repeating of this service
        ScheduleEventNotifications.stopServiceRepeating(this);
    }
}
