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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import android.util.Log;

import com.deltacontrols.eventviewer.App;
import com.deltacontrols.eweb.support.models.AlarmGroup;
import com.deltacontrols.eweb.support.models.iEvent;
import com.deltacontrols.eweb.support.models.iEvent.TransitionState;

/**
 * EventCache stores data about enteliWEB events (iEvent)
 * Note: Events must be added in increasing Index order
 */
public class EventCache implements Iterable<iEvent> {
    // ----------------------------------------------------------------------------------------------------------------
    // Properties
    // ----------------------------------------------------------------------------------------------------------------
    public static int EVENT_CACHE_MAX = 500;                // Max number of items that can be in the cache
    public HashMap<String, AlarmGroup> alarmGroupInfo;      // Summary of the alarm groups found in the current mEventCache, indexed on group name.

    private LinkedList<iEvent> mEventCache;                 // FIFO list; new events added to end of list; therefore ordered ASC on notification index.
    private ConcurrentHashMap<String, iEvent> mEventLookup; // Lookup event based on ID; avoids linked list traverse for lookup

    // ----------------------------------------------------------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------------------------------------------------------
    public EventCache() {
        init();
    }

    public EventCache(int limit) {
        EVENT_CACHE_MAX = limit;
        init();
    }

    private void init() {
        mEventCache = new LinkedList<iEvent>();
        mEventLookup = new ConcurrentHashMap<String, iEvent>();
        alarmGroupInfo = new HashMap<String, AlarmGroup>();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Public functionality
    // ----------------------------------------------------------------------------------------------------------------
    /**
     * Adds an event to the cache; runs through the existing items in the cache to correctly update and flag older 
     * transitions if they are related to the new event that was just added. The main goal
     * of this is to correctly indicate when a notification can be acknowledged or not, since we do not get that information
     * from enteliWEB
     */
    public void add(iEvent ev) {
        // Massage alarm text (take out newlines etc)
        ev.setMessage(ev.getMessage().replace("\n", " ").replace("\r", " "));

        // Set transition state
        String toState = ev.getToState();
        if (toState.equals("Normal")) {
            ev.currentState = TransitionState.NORMAL;
        }
        else if (toState.equals("Fault")) {
            ev.currentState = TransitionState.FAULT;
        }
        else {
            ev.currentState = TransitionState.OFF_NORMAL;
        }

        // Update alarm group summary info
        AlarmGroup group;
        if (alarmGroupInfo.containsKey(ev.getAlarmGroupName())) {
            group = alarmGroupInfo.get(ev.getAlarmGroupName());
            group.count = group.count + 1;
        }
        else {
            group = new AlarmGroup(ev.getAlarmGroupName(), ev.getAlarmGroupColor(), null);
            group.count = 1;
        }

        alarmGroupInfo.put(ev.getAlarmGroupName(), group);

        // Limit the list size
        if (mEventCache.size() >= EVENT_CACHE_MAX) {
            // Overflow, need to remove
            iEvent removed = mEventCache.remove();      // Remove from list
            mEventLookup.remove(removed.getIndex());    // Remove from lookup

            // Remove from alarm group summary?
            group = alarmGroupInfo.get(removed.getAlarmGroupName());
            group.count = Math.max(0, group.count - 1);
        }

        // Update based on action
        massageBasedOnAction(ev);

        // Compare against old entries to set stale and reset ack flags.
        compareAgainstOlderEntries(ev);

        // Finally, add new event to list(s)
        mEventCache.add(ev);
        mEventLookup.put(ev.getIndex(), ev);
    }

    /**
     * Adds a list of events to the cache.
     */
    public void addAll(ArrayList<iEvent> list) {
        for (iEvent ev : list) {
            this.add(ev);
        }
    }

    /**
     * Returns the index of the last notification in cache; usually used to help the service 
     * request the next batch of notifications.
     */
    public String getLastKnownIndex() {
        if (mEventCache.size() > 0) {
            return mEventCache.peekLast().getIndex();
        }
        else {
            return null;
        }
    }

    /**
     * Return a deep copy of the cached array This means that any clients that update the events must call back 
     * into the service to update the event.
     */
    public ArrayList<iEvent> getCopy() {
        ArrayList<iEvent> deepCopy = new ArrayList<iEvent>();
        for (iEvent ev : this.mEventCache) {
            deepCopy.add(new iEvent(ev));
        }
        return deepCopy;
    }

    /**
     * Clears the cache and lookup objects
     */
    public void clear() {
        mEventCache.clear();
        mEventLookup.clear();
        alarmGroupInfo.clear();
    }

    /**
     * Returns the number of notifications in cache; should never be greater than EVENT_CACHE_MAX.
     */
    public int size() {
        return mEventCache.size();
    }

    /**
     * Given an event, attempt to find it in cache and update it with the new values. Note, if the event no longer 
     * exists in cache, a log entry is made, but no exception is thrown.
     * 
     * @param ev
     */
    public void updateEvent(iEvent ev) {
        String index = ev.getIndex();
        if (mEventLookup.containsKey(index)) {
            mEventLookup.get(index).updateWith(ev); // Do not create new, will cause mEventLookup to no longer point to correct address space.
            Log.i(App.TAG, "updateEvent Ack'd: " + mEventLookup.get(index).getAcknowledged());
        }
        else {
            Log.i(App.TAG, String.format("Event %s : %s no longer exists in service cache", index, ev.getEventRef()));
        }
    }

    /**
     * Iterates over the linked mEventCache list.
     */
    @Override
    public Iterator<iEvent> iterator() {
        Iterator<iEvent> iEv = mEventCache.iterator();
        return iEv;
    }

    /**
     * Iterates over the linked mEventCache list.
     */
    public Iterator<iEvent> descendingIterator() {
        Iterator<iEvent> iEv = mEventCache.descendingIterator();
        return iEv;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Private functionality
    // ----------------------------------------------------------------------------------------------------------------
    /**
     * Updates the ack flag and message properties based on the notification action type
     */
    private void massageBasedOnAction(iEvent ev) {
        String action = ev.getAction();

        if (action.equals("ALARMACK")) {
            ev.setAsAcknowledged();
            String ackMessage = (ev.getMessage().isEmpty()) ? "" : " (" + ev.getMessage() + ")";
            ev.setMessage(String.format("Transition acknowledged%s", ackMessage));

            // By default the EventTimestamp will show the original transition time, but we want to show
            // when the ack occurred. Change the EventTimestamp to use the EnteliwebTimestamp instead.
            ev.setEventTimestamp(ev.getEnteliwebTimestamp());

        }
        else if (action.equals("ALARMASSIGNMENT")
                || action.equals("ALARMCOMMENT")
                || action.equals("FAULT")) {
            ev.setAsAcknowledged();
        }
        else {
            // Do nothing?
        }
    }

    /**
     * Runs through the mEventCache and mEventLookup to make sure that the mEventLookup contains an item for 
     * each object in the mEventCache. For testing purposes.
     * 
     * @return false if the objects do not match across mEventCache and mEventLookup
     */
    @SuppressWarnings("unused")
    private boolean verifyListAndLookupSync() {
        for (iEvent ev : this.mEventCache) {
            String index = ev.getIndex();

            if (mEventLookup.containsKey(index)) {
                if (!(ev == mEventLookup.get(index))) {
                    return false;
                }
            }
            else {
                return false;
            }
        }
        return true;
    }

    /**
     * The guts of the logic that determine if older items in the cache need to be updated to reflect a new stale or 
     * acknowledge state. Note that the cache is implemented as a FIFO linked list; this means the "oldest" transition is 
     * at the beginning of the list.
     */
    private void compareAgainstOlderEntries(iEvent ev) {
        // LinkedList backed by double linked list; use descending iterator to start at the end
        // (the newest) and work backwards (to the older) to determine if the new item now makes an older
        // transition 'stale'.
        Iterator<iEvent> iReverse = mEventCache.descendingIterator();
        iEvent olderEvent;

        boolean isAlarmAck = ev.getAction().equals(iEvent.TransitionAction.ALARMACK.toString());
        boolean isStatusChange = ev.getAction().equals(iEvent.TransitionAction.STATUSCHANGE.toString());

        while (iReverse.hasNext()) {
            olderEvent = iReverse.next();
            boolean isSameEvent = ev.getEventRef().equals(olderEvent.getEventRef());
            boolean isSameTransition = ev.currentState == olderEvent.currentState;

            // Manually update Acknowledged flag since it may be incorrect from eWEB in a few cases:
            // 1) If the transition is a STATUSCHANGE on the same event transition, then the older transition cannot be ack'd.
            // 2) If the transition is an ALARMACK on the same event transition, then we know the older transition cannot be ack'd.
            // Note that 2) is getting around an issue in eWEB where the Acknowledged property is still false even
            // after a successful api/event/ack.

            // Case 1)
            // Mark the older event as being a stale transition
            if (isStatusChange && isSameEvent) {
                olderEvent.staleTransition = true;

                // Check to see if toStates are the same, if yes, then remove any ack that may be on the olderEvent as it is no longer relevant
                // Note, use determined currentState, not toState (high-alarm and low-alarm are both "off-normal"
                if (isSameTransition) {
                    olderEvent.setAsAcknowledged();
                }
            }

            // Case 2)
            // Note, do not want to set as stale since event MAY still be active.
            if (isAlarmAck && isSameEvent) { // (1) & (2)
                if (isSameTransition) {
                    olderEvent.setAsAcknowledged();
                }
            }
        }
    }
}
