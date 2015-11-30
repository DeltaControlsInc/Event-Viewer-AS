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
import java.util.List;

import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.deltacontrols.eweb.support.models.iEvent;

/**
 * EventItemAdapter Adapter for the filterable list of event items
 */
public class EventItemAdapter extends ArrayAdapter<iEvent> implements Filterable {

    private Context mContext;
    private int mLayoutResourceId;

    private ArrayList<iEvent> mFullList;        // Full list of all events; used so we can easily revert filtered list.
    private ArrayList<iEvent> mFilteredList;    // Currently filtered list; subset of mFullList
    private EventItemFilter mFilter;            // Filter object; contains values for all filters
    private Drawable mAckIcon;                  // Reference to the ack flag icon

    public EventItemAdapter(Context context, int layoutResourceId, ArrayList<iEvent> data) {
        super(context, layoutResourceId, data);
        this.mLayoutResourceId = layoutResourceId;
        this.mContext = context;
        this.mFilteredList = data;              // Observable data.

        // Create copy of the original data; since we are changing our
        // observable data set (filteredList, we need to store the original).
        this.mFullList = new ArrayList<iEvent>();
        this.mFullList.addAll(this.mFilteredList);

        mAckIcon = context.getResources().getDrawable(R.drawable.ic_state_ack_req);
    }

    /**
     * Updates the fullList with a new data set and then notifies itself of the change.
     * 
     * @param data New list of data to show
     */
    public void updateData(ArrayList<iEvent> data) {
        this.mFullList.clear();
        this.mFullList.addAll(data);
        this.mFilteredList.clear();
        this.mFilteredList.addAll(data);
        this.notifyDataSetChanged();
    }

    /**
     * @SuppressWarnings("deprecation") Remove when we are supporting API 16+ (setBackgroundDrawable currently allowing us to set background on < 16).
     */
    @SuppressWarnings("deprecation")
    @Override
    public View getView(int position, View row, ViewGroup parent) {

        EventItemHolder holder = null;
        Resources res = getContext().getResources();

        if (row == null) {
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            row = inflater.inflate(mLayoutResourceId, parent, false);
            holder = new EventItemHolder();
            holder.eventName = (TextView) row.findViewById(R.id.eventName);
            holder.inputName = (TextView) row.findViewById(R.id.inputName);
            holder.eventTimestamp = (TextView) row.findViewById(R.id.eventTimestamp);
            holder.eventMessage = (TextView) row.findViewById(R.id.eventMessage);
            holder.categoryColorLayout = (RelativeLayout) row.findViewById(R.id.categoryColorLayout);
            holder.categoryIcon = (ImageView) row.findViewById(R.id.toStateIcon);
            holder.ackIcon = (ImageView) row.findViewById(R.id.ackStateIcon);
            row.setTag(holder);
        }
        else {
            holder = (EventItemHolder) row.getTag();
        }

        // Use event info to load row holder
        iEvent event = getItem(position);
        holder.eventName.setText(event.getEventName());
        holder.inputName.setText(event.getInputName());
        holder.eventTimestamp.setText(event.getEventTimestamp());
        holder.eventMessage.setText(event.getMessage());

        // Cannot set style programatically, so set "read/unread" look and feel here.
        if (event.hasBeenViewed) {
            row.setBackgroundDrawable(res.getDrawable(R.drawable.selector_summary_list_item_2));
            holder.eventName.setTypeface(null, Typeface.NORMAL);
            holder.inputName.setTypeface(null, Typeface.ITALIC);
            holder.eventTimestamp.setTypeface(null, Typeface.NORMAL);
            holder.eventMessage.setTypeface(null, Typeface.NORMAL);
        }
        else {
            row.setBackgroundDrawable(res.getDrawable(R.drawable.selector_summary_list_item_1));
            holder.eventName.setTypeface(null, Typeface.BOLD);
            holder.inputName.setTypeface(null, Typeface.BOLD_ITALIC);
            holder.eventTimestamp.setTypeface(null, Typeface.BOLD);
            holder.eventMessage.setTypeface(null, Typeface.BOLD);
        }

        // Change background DRAWABLE colour so that we preserve any other styling (ie. corner radius)
        GradientDrawable categoryColor = (GradientDrawable) holder.categoryColorLayout.getBackground();
        categoryColor.setColor(event.getCategoryColor(res.getColor(R.color.defaultCategoryColor)));

        // Update icons
        holder.categoryIcon.setImageResource(App.getToStateIcon(event));
        holder.ackIcon.setImageDrawable(event.ackRequired() ? mAckIcon : null);

        return row;
    }

    /**
     * List item view "holder"; contains references to all data outlets for each list item.
     */
    static class EventItemHolder {
        TextView inputName;
        TextView eventName;
        TextView eventTimestamp;
        TextView eventMessage;
        RelativeLayout categoryColorLayout;
        ImageView categoryIcon;
        ImageView ackIcon;
    }

    // --------------------------------------------------------------------------------
    // Implements Filter
    // --------------------------------------------------------------------------------
    /**
     * FilterBy is a collection of filter options; because EventItemFilter may only perform filtering on a string, we allow 
     * the callee to pass in a JSON string representing a FilterBy object which may
     * then be used to provide the filtering options.
     */
    private class FilterBy {
        String groupName;
        Boolean acked;
        Boolean active;
        String text;
        Integer priorityLow;
        Integer priorityHigh;

        public FilterBy() {
            setDefaults();
        }

        private void setDefaults() {
            groupName = null;
            acked = null;
            active = null;
            text = null;
            priorityLow = null;
            priorityHigh = null;
        }

        /**
         * SuppressLint("DefaultLocale") added until we come up with a good way to indicate global locale.
         */
        @SuppressLint("DefaultLocale")
        public FilterBy(JSONObject json) {
            try {
                groupName = json.has("groupName") ? json.getString("groupName") : null;
                acked = json.has("acked") ? json.getBoolean("acked") : null;
                active = json.has("active") ? json.getBoolean("active") : null;
                text = json.has("text") ? json.getString("text").toLowerCase() : null; // Note, forced lower case
                priorityLow = json.has("priorityLow") ? json.getInt("priorityLow") : null;
                priorityHigh = json.has("priorityHigh") ? json.getInt("priorityHigh") : null;
            } catch (Exception e) {
                Context ctx = App.getContext();
                ShowCustomToast(ctx, ctx.getResources().getString(R.string.could_not_parse_filter), Toast.LENGTH_SHORT);                
                setDefaults();
            }
        }

        public boolean hasConstraints() {
            return !((groupName == null) && (acked == null) && (active == null) && (text == null)
                    && (priorityLow == null) && (priorityHigh == null));
        }
    }

    /**
     * EventItemFilter Provides custom filtering for the adapter Allows us to apply multiple filters on the
     */
    private class EventItemFilter extends Filter {
        /**
         * Filter the list using the filter options given in the constraint JSON string.
         * 
         * @param constraint JSON String representing a {@link FilterBy} object.
         */
        @SuppressLint("DefaultLocale")
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            FilterBy filters;

            try {
                JSONObject json = new JSONObject(constraint.toString());
                filters = new FilterBy(json);
            } catch (Exception e) {
                filters = new FilterBy(); // No constraints
                Context ctx = App.getContext();
                ShowCustomToast(ctx, ctx.getResources().getString(R.string.could_not_parse_filter), Toast.LENGTH_SHORT);                 
            }

            // We implement here the filter logic
            if (filters.hasConstraints()) {

                List<iEvent> resultList = new ArrayList<iEvent>();

                for (iEvent item : mFullList) {

                    // Check matches alarm group name
                    boolean matchesGroupName = (filters.groupName == null) || item.getAlarmGroupName().equals(filters.groupName);

                    // Check event name
                    boolean matchesEventName = true;

                    if (item.getEventName() == null) {
                        matchesEventName = (filters.text == null); // Only list no name events if the name filter is null
                    }
                    else {
                        // Else, event matches if we have no filter, or if the event name contains the filter string
                        matchesEventName = (filters.text == null) || item.getEventName().toLowerCase().contains(filters.text);
                    }

                    // Check monitored input name
                    boolean matchesObjectName = true;
                    if (item.getInputName() == null) {
                        matchesObjectName = (filters.text == null); // Only list no name events if the name filter is null
                    }
                    else {
                        // Else, event matches if we have no filter, or if the event name contains the filter string
                        matchesObjectName = (filters.text == null) || item.getInputName().toLowerCase().contains(filters.text);
                    }

                    // Check event message
                    boolean matchesMessageMessage = true;
                    if (item.getMessage() == null) {
                        matchesMessageMessage = (filters.text == null); // Only list no name events if the name filter is null
                    }
                    else {
                        // Else, event matches if we have no filter, or if the event name contains the filter string
                        matchesMessageMessage = (filters.text == null) || item.getMessage().toLowerCase().contains(filters.text);
                    }

                    // Final decision; must match ALL cases to be shown (ie. filters are ADDITIVE to each other)
                    if (matchesGroupName
                            && (matchesEventName || matchesObjectName || matchesMessageMessage)) { // name could be either event (OR) object
                        resultList.add(item);
                    }
                }

                results.values = resultList;
                results.count = resultList.size();
            }
            else {
                // No constraints' we return the full list
                results.values = mFullList;
                results.count = mFullList.size();
            }

            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // Clear and then add all values to force the listAdapter to observe
            // the change; simply setting and then calling notify does not work.
            mFilteredList.clear();
            mFilteredList.addAll((ArrayList<iEvent>) results.values);
            notifyDataSetChanged();
        }
    }

    @Override
    public Filter getFilter() {
        if (mFilter == null)
            mFilter = new EventItemFilter();

        return mFilter;
    }
    
	public void ShowCustomToast(Context context, String textMessage, int duration) {
    	Toast cusToast = Toast.makeText(context, textMessage, duration);
    	
    	cusToast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
    	cusToast.show();	    	
	}	    
}
