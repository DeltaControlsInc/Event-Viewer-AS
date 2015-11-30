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

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.deltacontrols.eweb.support.models.AlarmGroup;

/**
 * Adapter for the alarm group spinner (which displays alarm group color and name)
 */
public class GroupSpinnerItemAdapter extends ArrayAdapter<AlarmGroup> /* implements Filterable */{

    private Context mContext;
    private int mLayoutResourceId;

    private ArrayList<AlarmGroup> mFullList;
    private ArrayList<AlarmGroup> mFilteredList;

    public GroupSpinnerItemAdapter(Context context, int layoutResourceId, ArrayList<AlarmGroup> data) {
        super(context, layoutResourceId, data);
        this.mLayoutResourceId = layoutResourceId;
        this.mContext = context;
        this.mFilteredList = data; // Observable data.

        // Create copy of the original data; since we are changing our
        // observable data set (filteredList, we need to store the original).
        this.mFullList = new ArrayList<AlarmGroup>();
        this.mFullList.addAll(this.mFilteredList);
    }

    /**
     * Updates the fullList with a new data set and then notifies itself of the change.
     * 
     * @param data new data to be shown
     */
    public void updateData(ArrayList<AlarmGroup> data) {
        this.mFullList.clear();
        this.mFullList.addAll(data);
        this.mFilteredList.clear();
        this.mFilteredList.addAll(data);
        this.notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        Context ctx = getContext();
        GroupSpinnerItemHolder holder = null;

        if (row == null) {
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            row = inflater.inflate(mLayoutResourceId, parent, false);
            holder = new GroupSpinnerItemHolder();
            holder.groupSpinnerItemName = (TextView) row.findViewById(R.id.groupSpinnerItemName);
            holder.groupSpinnerItemColor = (RelativeLayout) row.findViewById(R.id.groupSpinnerItemColor);

            row.setTag(holder);
        }
        else {
            holder = (GroupSpinnerItemHolder) row.getTag();
        }

        // Use event info to load row holder
        AlarmGroup group = getItem(position); // data[position];
        String text = (group.count > 0) ? String.format("%s (%s)", group.name, group.count) : group.name;
        holder.groupSpinnerItemName.setText(text);

        // Change background DRAWABLE colour so that we preserve any other styling (ie. corner radius)
        GradientDrawable categoryColor = (GradientDrawable) holder.groupSpinnerItemColor.getBackground();
        try {
            categoryColor.setColor(Color.parseColor(group.color));
        } catch (Exception e) {
            categoryColor.setColor(ctx.getResources().getColor(R.color.lightestGrey));
        }

        return row;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    /**
     * List item view "holder"; contains references to all data outlets for each list item.
     */
    static class GroupSpinnerItemHolder {
        TextView groupSpinnerItemName;
        RelativeLayout groupSpinnerItemColor;
    }
}
