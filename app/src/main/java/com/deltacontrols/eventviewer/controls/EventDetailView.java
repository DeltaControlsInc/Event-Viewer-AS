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
package com.deltacontrols.eventviewer.controls;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.deltacontrols.eventviewer.R;

/**
 * Provides easy way to set "label: value" for each of the event details.
 */
public class EventDetailView extends LinearLayout {

    // Outlets
    private TextView mLabel;
    private TextView mValue;
    private Context mCtx;
    private int mLayoutID = R.layout.view_event_detail;

    public EventDetailView(Context context) {
        super(context);
        init(context, "", "");
    }

    public EventDetailView(Context context, int layout) {
        super(context);
        mLayoutID = layout;
        init(context, "", "");
    }

    public EventDetailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initWithAttributes(context, attrs);
    }

    public EventDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initWithAttributes(context, attrs);
    }

    private void initWithAttributes(Context context, AttributeSet attrs) {
        String label;
        String value;

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EventDetailView, 0, 0);
        try {
            label = a.getString(R.styleable.EventDetailView_label);
            value = a.getString(R.styleable.EventDetailView_value);
        } 
        catch (Exception e) {
            label = "";
            value = "";
        } 
        finally {
            a.recycle();
        }

        init(context, label, value);
    }

    private void init(Context context, String label, String value) {
        mCtx = context;
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(mLayoutID, this);
        mLabel = (TextView) this.findViewById(R.id.event_detail_label);
        mValue = (TextView) this.findViewById(R.id.event_detail_value);
        set(label, value);
    }

    public void set(String label, String value) {
        setLabel(label);
        setValue(value);
    }

    public void setHTML(String label, String htmlText, OnClickListener clickListener) {
        mLabel.setText(label);
        mValue.setText(Html.fromHtml(htmlText));
        mValue.setTextColor(mCtx.getResources().getColor(R.color.DeltaLightBlue));
        mValue.setOnClickListener(clickListener);
    }

    public void setLabel(String label) {
        mLabel.setText(label);
    }

    public void setValue(String value) {
        mValue.setText(value);
    }

}
