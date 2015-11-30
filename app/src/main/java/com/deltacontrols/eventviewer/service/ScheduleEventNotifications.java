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

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.deltacontrols.eventviewer.App;
import com.deltacontrols.eventviewer.LoginInfo;
import com.deltacontrols.eventviewer.service.EventNotificationsService;

/**
 * Schedules EventNotificationsService to be run at "regular" intervals. Could be called either on app startup, or via boot receiver
 */
public class ScheduleEventNotifications {

    private static PendingIntent mPendingIntent;

    // Delay time before starting the timer if none is given
    private static int mServiceDelay = 0;

    public static PendingIntent getStartIntent() {
        return mPendingIntent;
    }

    // Could be called by BOOT or by app first launching.
    public static void startServiceRepeating(Context context, int serviceDelay, int repeatSeconds) {
        // Check if the service is already scheduled
        if (mPendingIntent != null) {
            Log.i(App.TAG, "ScheduleEventNotifications::startServiceRepeating SERVICE ALREADY SCHEDULED");
            return;
        }

        Log.i(App.TAG, "ScheduleEventNotifications::startServiceRepeating");

        AlarmManager service = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, EventNotificationsService.class); // Service that grabs intent
        mPendingIntent = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);

        // Delay for starting the repeating service (should be longer on boot to avoid slowing down a device startup
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, mServiceDelay);

        // fetch every X seconds
        // InexactRepeating allows Android to optimize the energy consumption
        // Note, if you would like the device to be woken up when an alarm occurs, use RTC_WAKEUP instead of RTC
        service.setInexactRepeating(AlarmManager.RTC, cal.getTimeInMillis(), repeatSeconds * 1000, mPendingIntent);
    }

    public static void startServiceRepeating(Context context) {
        LoginInfo login = LoginInfo.getLoginInfo(context);
        startServiceRepeating(context, mServiceDelay, login.refreshSeconds); // Use last set values
    }

    public static void stopServiceRepeating(Context context) {
        Log.i(App.TAG, "ScheduleEventNotifications::stopServiceRepeating");
        AlarmManager service = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        service.cancel(mPendingIntent);
        mPendingIntent = null;
    }

}
