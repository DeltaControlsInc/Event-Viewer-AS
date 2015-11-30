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

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Class wrapping all login information for a user, including their preferences. 
 * Started out with only 4 properties, so shared storage made sense; long term may want to change to storing a json
 * object for the login info instead of all the properties individually.
 */
public class LoginInfo {
    // Currently stored in shared preferences
    private static String SHARED_PREF_ID = "EVENTVIEWER_SHARED_PREFS";
    private static String SHARED_PREF_SERVER_ID = "SERVER";
    private static String SHARED_PREF_USERNAME_ID = "USERNAME";
    private static String SHARED_PREF_PASSWORD_ID = "PASSWORD";
    private static String SHARED_PREF_ACTIVE_ID = "ACTIVE";
    private static String SHARED_PREF_REFRESH_ID = "REFRESH";
    private static String SHARED_PREF_DISMISSINDEX_ID = "DISMISSINDEX";
    private static String SHARED_PREF_BASIC_AUTHETICATION_ID = "BASIC_AUTHENTIACATION";

    public String url;          // eWEB url
    public String username;     // eWEB username
    public String password;     // eWEB password
    public boolean active;      // If the user is considered currently active/logged in
    public int refreshSeconds;  // Number of seconds between each notification refresh
    public String dismissIndex; // Index value when the user last dismissed the event list
    public boolean mBasicAuthentication = true; // By default use basic authentication method

    private static boolean mDefaultActive = false;      // By default user is not logged in
    private static int mDefaultRefreshSeconds = 300;    // By default service runs every X seconds; 300 = 5 mins
    private static String mDefaultDismissIndex = null;  // By default no dimiss index is set

    // ------------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------------
    public LoginInfo() {
        this(null, null, null, mDefaultRefreshSeconds, mDefaultDismissIndex, mDefaultActive, true);
    }

    public LoginInfo(String url, String username, String password) {
        this(url, username, password, mDefaultRefreshSeconds, mDefaultDismissIndex, mDefaultActive, true);
    }

    public LoginInfo(String url, String username, String password, boolean active) {
        this(url, username, password, mDefaultRefreshSeconds, mDefaultDismissIndex, active, true);
    }

    public LoginInfo(String url, String username, String password, int refreshSeconds, String dismissIndex, boolean active, boolean basicAuthentication) {
        this.url = (url == null) ? "" : url;
        this.username = (username == null) ? "" : username;
        this.password = (password == null) ? "" : password;
        this.refreshSeconds = refreshSeconds;
        this.dismissIndex = dismissIndex;
        this.active = active;
        this.mBasicAuthentication = basicAuthentication;
    }

    // ------------------------------------------------------------------------------
    // Auto generated hashCode and equals functions.
    // ------------------------------------------------------------------------------
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((password == null) ? 0 : password.hashCode());
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        result = prime * result
                + ((username == null) ? 0 : username.hashCode());
        return result;
    }

    /**
     * Login info is equal if the password, name and url contain the same string value.
     * NOTE Equals does not take "active" or "refreshSeconds" into account.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LoginInfo other = (LoginInfo) obj;
        if (password == null) {
            if (other.password != null)
                return false;
        } else if (!password.equals(other.password))
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        if (username == null) {
            if (other.username != null)
                return false;
        } else if (!username.equals(other.username))
            return false;
        return true;
    }

    // ------------------------------------------------------------------------------
    // Static functions
    // ------------------------------------------------------------------------------
    /**
     * Get login info for user stored in shared preferences
     */
    public static LoginInfo getLoginInfo(Context ctx) {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_ID, Context.MODE_PRIVATE);
        return new LoginInfo(
                sharedPreferences.getString(SHARED_PREF_SERVER_ID, ""),
                sharedPreferences.getString(SHARED_PREF_USERNAME_ID, ""),
                sharedPreferences.getString(SHARED_PREF_PASSWORD_ID, ""),
                sharedPreferences.getInt(SHARED_PREF_REFRESH_ID, mDefaultRefreshSeconds),
                sharedPreferences.getString(SHARED_PREF_DISMISSINDEX_ID, mDefaultDismissIndex),
                sharedPreferences.getBoolean(SHARED_PREF_ACTIVE_ID, mDefaultActive),
                sharedPreferences.getBoolean(SHARED_PREF_BASIC_AUTHETICATION_ID, true));
    }

    /**
     * Sets the shared preferences to contain the given login info.
     */
    public static void setLoginInfo(Context ctx, LoginInfo details) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(SHARED_PREF_ID, Context.MODE_PRIVATE).edit();
        editor.putString(SHARED_PREF_SERVER_ID, details.url);
        editor.putString(SHARED_PREF_USERNAME_ID, details.username);
        editor.putString(SHARED_PREF_PASSWORD_ID, details.password);
        editor.putInt(SHARED_PREF_REFRESH_ID, details.refreshSeconds);
        editor.putString(SHARED_PREF_DISMISSINDEX_ID, details.dismissIndex);
        editor.putBoolean(SHARED_PREF_ACTIVE_ID, details.active);
        editor.putBoolean(SHARED_PREF_BASIC_AUTHETICATION_ID, details.mBasicAuthentication);
        editor.commit();

    }

    public static void deleteStoredLoginInfo(Context ctx) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(SHARED_PREF_ID, Context.MODE_PRIVATE).edit();
        editor.clear().commit();
    }

    /**
     * True if login url, user and password are setup
     */
    public static boolean storedLoginExists(Context ctx) {
        LoginInfo storedLogin = LoginInfo.getLoginInfo(ctx);
        return !((storedLogin.url.equals("")) || (storedLogin.username.equals("")) || (storedLogin.password.equals("")));
    }

    /**
     * True if there is a stored user, and they are currently active/logged in
     */
    public static boolean storedUserIsActive(Context ctx) {
        LoginInfo storedLogin = LoginInfo.getLoginInfo(ctx);
        boolean setup = !((storedLogin.url.equals("")) || (storedLogin.username.equals("")) || (storedLogin.password.equals("")));
        return setup && storedLogin.active;
    }
}