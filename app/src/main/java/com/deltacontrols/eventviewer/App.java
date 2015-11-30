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

import android.app.Application;
import android.content.Context;
import android.util.DisplayMetrics;

import com.deltacontrols.eweb.support.api.EwebConnection;
import com.deltacontrols.eweb.support.models.iEvent;

/**
 * App Global Application object: Contains static functionality that pertains to
 * the entire application Also provides an easy way for us to access context.
 */
public class App extends Application {

    public final static String TAG = "EventViewer";
    private static Context mContext;

    // EWeb connection (only ONE for the entire application)
    private static EwebConnection mEwebConnection;

    public static EwebConnection getEwebConnection() {
        if (mEwebConnection == null) {
            mEwebConnection = new EwebConnection();
        }
        return mEwebConnection;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
    }

    public static Context getContext() {
        return mContext;
    }

    // -------------------------------------------------------------------------------------------------
    // Static functionality
    // Provides get/set functionality allowing us to switch between demo and
    // actual interactions with the server.
    // -------------------------------------------------------------------------------------------------
    /**
     * Use local resources to get icon; in the future we may want to move this
     * to the API library Not sure if this is the best place for this.
     * 
     * @return
     */
    public static int getToStateIcon(iEvent ev) {
        int iconResource = R.drawable.ic_state_alarm; // Many kinds of alarm  (low, high etc)

        if ((ev.getToState().equals("Fault")) || (ev.getToState().equals("fault"))) {
            iconResource = R.drawable.ic_state_fault;
        }
        else if ((ev.getToState().equals("Normal")) || ev.getToState().equals("normal")) {
            iconResource = R.drawable.ic_state_normal;
        }

        return iconResource;
    }

    public static int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return px;
    }

    public static int pxToDp(int px) {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int dp = Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return dp;
    }
}
