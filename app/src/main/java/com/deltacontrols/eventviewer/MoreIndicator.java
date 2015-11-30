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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Creates the view for the up/down (more) indicators that appear at the header and footer of the event list.
 */
public class MoreIndicator extends View {
    private Paint mBackPaint;
    private Paint mLinePaint;
    private Path lines;

    private int xFactor = 10;
    private int yFactor = 4;
    private int lineSpace = 8;

    private int mDefaultBackColor = R.color.lightestGrey;
    private int mDefaultLineColor = R.color.DeltaDarkGrey;

    public enum Direction {
        UP, DOWN
    };

    private Direction mDirection;

    public void setDirection(Direction dir) {
        mDirection = dir;
        this.invalidate();
    }

    public MoreIndicator(Context context) {
        super(context);
        setupPaint(View.NO_ID, View.NO_ID);
    }

    public MoreIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);

        int[] attrsArray = new int[] {
                android.R.attr.background, // 0
                android.R.attr.textColor // 1
        };

        TypedArray ta = context.obtainStyledAttributes(attrs, attrsArray);
        setupPaint(ta.getResourceId(0, View.NO_ID), ta.getResourceId(1, View.NO_ID));
        ta.recycle();
    }

    public MoreIndicator(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs); // Not using defStyle
    }

    private void setupPaint(int backID, int lineID) {
        lines = new Path();
        mDirection = Direction.DOWN;

        mBackPaint = new Paint();
        mBackPaint.setStyle(Paint.Style.FILL);
        mBackPaint.setColor(getResources().getColor(backID == View.NO_ID ? mDefaultBackColor : backID));

        mLinePaint = new Paint();
        mLinePaint.setStyle(Paint.Style.STROKE);
        mBackPaint.setAntiAlias(true);
        mLinePaint.setColor(getResources().getColor(lineID == View.NO_ID ? mDefaultLineColor : lineID));
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Paint background color
        int height = getHeight();
        int width = getWidth();

        // Background
        canvas.drawRect(0, 0, width, height, mBackPaint);

        // Lines
        int arrowWidth = width / xFactor;
        int arrowHeight = height / yFactor;
        int middleX = width / 2;
        int middleY = height / 2;

        if (mDirection == Direction.UP) {
            arrowHeight = arrowHeight * -1;
        }

        lines.reset();
        // Top line
        lines.moveTo(middleX - (arrowWidth / 2), middleY - (arrowHeight / 2));
        lines.lineTo(width / 2, middleY + (arrowHeight / 2));
        lines.lineTo(middleX + (arrowWidth / 2), middleY - (arrowHeight / 2));

        // Bottom line
        lines.moveTo(middleX - (arrowWidth / 2), middleY - (arrowHeight / 2) + lineSpace);
        lines.lineTo(width / 2, middleY + (arrowHeight / 2) + lineSpace);
        lines.lineTo(middleX + (arrowWidth / 2), middleY - (arrowHeight / 2) + lineSpace);

        canvas.drawPath(lines, mLinePaint);

    }
}
