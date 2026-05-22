package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayout extends ViewGroup {

    private int mHGap = 4;
    private int mVGap = 4;

    public FlowLayout(Context ctx) { super(ctx); }
    public FlowLayout(Context ctx, AttributeSet attrs) { super(ctx, attrs); }
    public FlowLayout(Context ctx, AttributeSet attrs, int defStyle) { super(ctx, attrs, defStyle); }

    private static int specFor(int lpDim) {
        if (lpDim >= 0) return MeasureSpec.makeMeasureSpec(lpDim, MeasureSpec.EXACTLY);
        return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, rowH = 0, totalH = 0;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            child.measure(specFor(lp.width), specFor(lp.height));
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                totalH += rowH + mVGap;
                x = 0; rowH = 0;
            }
            x += cw + mHGap;
            rowH = Math.max(rowH, ch);
        }
        totalH += rowH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(
            MeasureSpec.getSize(widthSpec),
            resolveSize(totalH, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), rowH = 0;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x + cw > getPaddingLeft() + maxW) {
                y += rowH + mVGap;
                x = getPaddingLeft(); rowH = 0;
            }
            child.layout(x, y, x + cw, y + ch);
            x += cw + mHGap;
            rowH = Math.max(rowH, ch);
        }
    }
}
