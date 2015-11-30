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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import android.net.Uri;
import android.util.Log;

import com.deltacontrols.eventviewer.service.EventCache;
import com.deltacontrols.eventviewer.service.EventNotificationsService;
import com.deltacontrols.eventviewer.service.EventNotificationsService.STATUS;
import com.deltacontrols.eweb.support.api.EwebConnection;
import com.deltacontrols.eweb.support.api.FetchJSON;
import com.deltacontrols.eweb.support.api.FetchRawResponse.RequestMethod;
import com.deltacontrols.eweb.support.api.FetchXML;
import com.deltacontrols.eweb.support.interfaces.GenericCallback;
import com.deltacontrols.eweb.support.models.AlarmGroup;
import com.deltacontrols.eweb.support.models.UserList;
import com.deltacontrols.eweb.support.models.iEvent;
import com.deltacontrols.eweb.support.models.iEventList;
import com.google.gson.Gson;

/**
 * ServiceWrapper exists to allow an easy switch between actual service functionality and mock data for a demo.
 * When running normally, the ServiceWrapper will allow interaction with the EventViewer service and communicate with eWEB directly.
 * When running as a demo, the ServiceWrapper provides mock values and interactions; the EventViewer service and eWEB are not used during a demo.
 * 
 * Note: Could have probably created an interface containing the functionality and then have the service and the demo classes implement that
 * interface, however, there was not enough time to refactor to that.
 */
public class ServiceWrapper {

    // ------------------------------------------------------------------------------
    // Properties
    // ------------------------------------------------------------------------------
    /**
     * Reference to the actual EventNotificationsService if one exists. May be null if we are in demo mode
     */
    private EventNotificationsService mNotificationService;

    public void setService(EventNotificationsService service) {
        mNotificationService = service;
    }

    /**
     * Indicates if we want to show demo or actual data
     */
    private boolean isDemo;

    public void setIsDemo(boolean demo) {
        // Load cache
        isDemo = demo;
        if (isDemo) {
            setupDemoCache();
        }

    }

    public boolean getIsDemo() {
        return isDemo;
    }

    /**
     * If in demo mode, this will mock up the event list cache that normally comes from the service
     */
    private EventCache demoCache;

    /**
     * List of users to be shown in the "assign to" drop down This could be empty if we have not contacted eWEB yet (see getUserList method).
     */
    public UserList userList;

    // ------------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------------
    public ServiceWrapper() {
        init();
    }

    public ServiceWrapper(boolean demo) {
        init();
        setIsDemo(demo);
    }

    public void init() {
        mNotificationService = null;
        isDemo = false;
        userList = new UserList();
        demoCache = new EventCache();
    }

    // ------------------------------------------------------------------------------
    // Private methods
    // ------------------------------------------------------------------------------
    /**
     * Loads fake data from a raw file into the demoCache
     */
    private void setupDemoCache() {
        demoCache.clear();
        JSONObject demoJSON = spoofJSONResult(R.raw.fake_events);
        iEventList demoEventList = iEventList.fromJson(demoJSON);
        demoCache.addAll(demoEventList.events);
    }

    // ------------------------------------------------------------------------------
    // Interaction with the service (or mock service)
    // ------------------------------------------------------------------------------
    /**
     * Demo: Returns a copy of the demo cache 
     * Live: Returns a copy of the service cache; also clears service flags for count and notifications
     */
    public ArrayList<iEvent> getEventList() {
        ArrayList<iEvent> result = null;

        if (isDemo) {
            result = demoCache.getCopy();
        }
        else {
            result = mNotificationService.getEventCacheCopy();

            // Remove system notification card if any are present
            mNotificationService.resetNewEventCount(); // Tell service we have viewed the new events
            mNotificationService.clearSystemNotification();
        }

        return result;
    }

    /**
     * Demo: Returns 0; for demo we assume no new events 
     * Live: Returns the new event count of the service
     */
    public long getNewEventCount() {
        long result = 0;

        if (isDemo) {
            // Do nothing, use 0
        }
        else {
            result = mNotificationService.getNewEventCount();
        }

        return result;
    }

    /**
     * Demo: Returns STATUS.OK; 
     * Live: Returns the current status of the service
     */
    public EventNotificationsService.STATUS getCurrentStatus() {
        EventNotificationsService.STATUS result = STATUS.UNKNOWN;

        if (isDemo) {
            result = STATUS.OK;
        }
        else {
            result = mNotificationService.getCurrentStatus();
        }

        return result;
    }

    /**
     * Demo: Returns a Date corresponding to now 
     * Live: Returns the Date of the last successful "get" from the service
     */
    public Date getLastSuccess() {
        Date result = null;

        if (isDemo) {
            result = new Date();
        }
        else {
            result = mNotificationService.getLastSuccess();
        }

        return result;
    }

    /**
     * Demo: Returns the alarmGroupInfo based on the demoCache 
     * Live: Returns the alarmGroupInfo based on the the events cached in the service
     */
    public HashMap<String, AlarmGroup> getAlarmGroupInfo() {
        HashMap<String, AlarmGroup> result = null;

        if (isDemo) {
            result = demoCache.alarmGroupInfo;
        }
        else {
            result = mNotificationService.getEventCacheAlarmGroupInfo();
        }

        return result;
    }

    /**
     * Demo: Update the event in the demoCache 
     * Live: Update the event in the service cached events
     */
    public void updateEventInCache(iEvent selectedEvent) {

        if (isDemo) {
            demoCache.updateEvent(selectedEvent);
        }
        else {
            mNotificationService.updateEventInCache(selectedEvent);
        }
    }

    /**
     * Demo: Clears demo cache 
     * Live: Clears service cache
     */
    public void dimissAllEvents() {
        if (isDemo) {
            demoCache.clear();
        }
        else {
            // Save last index as dismissIndex and then tell the service to clear its cache.
            LoginInfo login = LoginInfo.getLoginInfo(App.getContext());
            login.dismissIndex = mNotificationService.getLastKnownIndex();
            LoginInfo.setLoginInfo(App.getContext(), login);
            // Tell service to clear
            mNotificationService.clearCache();
        }
    }

    public void readAllEvents() {
        if (isDemo) {
            for (iEvent event : demoCache) {
                event.hasBeenViewed = true;
            }
        }
        else {
            mNotificationService.markAllAsRead();
        }
    }

    public void unreadAllEvents() {
        if (isDemo) {
            for (iEvent event : demoCache) {
                event.hasBeenViewed = false;
            }
        }
        else {
            mNotificationService.markAllAsUnread();
        }
    }

    /**
     * Demo: Does nothing, we are not actually logged in 
     * Live: Updates shared settings and then logs out of service
     */
    public void logout() {
        if (isDemo) {
            // Do nothing! We are not actually logged in
        }
        else {
            // Save last index as dismissIndex and then tell the service to clear its cache.
            LoginInfo login = LoginInfo.getLoginInfo(App.getContext());
            login.active = false;
            LoginInfo.setLoginInfo(App.getContext(), login);
            // Tell service to logout
            mNotificationService.logout();
        }
    }

    // ------------------------------------------------------------------------------
    // Direct interaction with eWEB (or mock interaction if demo)
    // Because of async interaction with eWEB, each of these functions requires a callback
    // ------------------------------------------------------------------------------
    /**
     * Demo: Returns a mock UserList 
     * Live: Calls eweb.getUserList to get the list of users
     */
    public void getUserList(final GenericCallback<UserList> callback) {

        UserList result = null;

        if (isDemo) {
            result = new UserList();
            result.add("Admin");
            result.add("Jane");
            result.add("John");
            callback.onCallback(result);
        }
        else {
            LoginInfo login = LoginInfo.getLoginInfo(App.getContext());
            EwebConnection eweb = App.getEwebConnection();
            eweb.getUserList(new GenericCallback<UserList>() {
                @Override
                public void onCallback(UserList result) {
                    if (result.size() > 1) {
                        result.add(0, "");
                    }
                    callback.onCallback(result);
                }
            });
        }
    }

    /**
     * Demo: Always returns the default AlarmDetails for the event. 
     *   If in future the "api/event" call no longer returns AlarmDetails, we will need to spoof details here. 
     * Live: Calls /api/event/<X>/AlarmDetails to get up-to-date info on the event
     */
    public void getAlarmDetails(iEvent event, final GenericCallback<iEvent.iAlarmDetails> callback) {
        if (isDemo) {
            // Use details from initial event JSON
            callback.onCallback(event.AlarmDetails);
        }
        else {
            EwebConnection eweb = App.getEwebConnection();
            eweb.getAlarmDetails(event, callback);
        }
    }

    /**
     * Demo: Always returns a spoofed "successful" FetchXML.Result (as would normally be returned from PUT /api/event/<X>/AlarmDetails) 
     * Live: Calls PUT /api/event/<X>/AlarmDetails to attempt to update the alarm details for the given event
     */
    public void setAlarmDetails(iEvent event, GenericCallback<FetchXML.Result> callback) {
        if (isDemo) {
            // Simulate an ok response and callback.
            // Note: At the time of implementation this was how eweb returned a successful ack. If the result changes we will need to update to match.
            String xmlSuccess = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Struct xmlns=\"http://bacnet.org/csml/1.2\">" +
                    "<String name=\"Assignee\" value=\"demo\"/><String name=\"Text\" value=\"demo\"/></Struct>";
            FetchXML.Result spoofResult = spoofFetchXMLResult(xmlSuccess);
            callback.onCallback(spoofResult);
        }
        else {
            EwebConnection eweb = App.getEwebConnection();
            eweb.setAlarmDetails(event, callback);
        }
    }

    /**
     * Demo: Always returns a spoofed "successful" FetchXML.Result (as would normally be returned from a call to eweb.acknowledgeEvent) 
     * Live: Calls eweb.acknowledgeEvent to attempt to acknowledge the given event
     */
    public void acknowledgeEvent(iEvent event, String eventMessage, GenericCallback<FetchXML.Result> callback) {
        if (isDemo) {
            // Simulate an ok response and callback.
            // Note: At the time of implementation this was how eweb returned a successful ack. If the result changes we will need to update to match.
            String xmlSuccess = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<String xmlns=\"http://bacnet.org/csml/1.1\" name=\"result\" value=\"\" error=\"-1\" errorText=\"OK\"></String>";
            FetchXML.Result spoofResult = spoofFetchXMLResult(xmlSuccess);
            callback.onCallback(spoofResult);
        }
        else {
            // Setup eweb connection to make ack
            EwebConnection eweb = App.getEwebConnection();
            // Attempt to ack event on server
            eweb.acknowledgeEvent(event, eventMessage, callback);
        }
    }

    // ------------------------------------------------------------------------------
    // Helper functions to recreate xml and json results for demo
    // ------------------------------------------------------------------------------
    private static FetchXML.Result spoofFetchXMLResult(String xmlResult) {
        boolean success = true;
        Document doc = null;
        try {
            doc = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xmlResult)));
        } catch (Exception e) {
            Log.e(App.TAG, "Problem parsing demo XML, please verify XML string");
            doc = null;
            success = false;
        }

        FetchXML.Result spoofResult = new FetchXML.Result();
        spoofResult.rawResponse = xmlResult;
        spoofResult.success = success;
        spoofResult.statusCode = HttpStatus.SC_OK;
        spoofResult.xml = doc;
        return spoofResult;
    }

    private static JSONObject spoofJSONResult(int fileID) {
        JSONObject result = null;

        try {
            // Parse response body as JSON.
            InputStream inputStream = App.getContext().getResources().openRawResource(fileID);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();

            for (String line = null; (line = reader.readLine()) != null;) {
                builder.append(line).append("\n");
            }

            String resultStr = builder.toString();
            JSONTokener tokener = new JSONTokener(resultStr);

            // Build up fake result
            result = new JSONObject(tokener);

        } catch (Exception ex) {
            result = null;
            ex.printStackTrace();
        }

        return result;
    }

}
