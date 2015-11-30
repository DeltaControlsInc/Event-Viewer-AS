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
/**
 * http://stackoverflow.com/questions/11195801/start-service-schedule-without-reboot
 */

package com.deltacontrols.eventviewer.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.deltacontrols.eventviewer.App;
import com.deltacontrols.eventviewer.LoginInfo;

public class ScheduleEventNotificationsBootReceiver extends BroadcastReceiver {

    private static final int BOOT_DELAY_TIME_S = 30; // Wait 30 seconds on boot to start the service so we don't bog down the device startup

    @Override
    public void onReceive(Context context, Intent intent) {

        // Check for stored user creds - if none saved, or if the stored user is not active, do not start the service.
        if (LoginInfo.storedUserIsActive(context)) {
            // If saved, start the service with the given boot and repeat time.
            LoginInfo checkLogin = LoginInfo.getLoginInfo(App.getContext());
            ScheduleEventNotifications.startServiceRepeating(context, BOOT_DELAY_TIME_S, checkLogin.refreshSeconds);
        }
        else {
            Log.i(App.TAG, "ScheduleEventNotificationsBootReceiver called, but no user is logged in");
        }
    }
}
