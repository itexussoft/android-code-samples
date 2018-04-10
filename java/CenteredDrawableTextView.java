package com.boomylabs.listly.ui.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.boomylabs.listly.R;

public class CenteredDrawableTextView extends AppCompatTextView {
    private static final int LEFT = 0, RIGHT = 2;
    private static final int TOP = 1, BOTTOM = 3;
    private static final int PERFORMED_LEFT_OFFSET = 0x1;
    private static final int PERFORMED_RIGHT_OFFSET = 0x2;
    private static final int PERFORMED_TOP_OFFSET = 0x4;
    private static final int PERFORMED_BOTTOM_OFFSET = 0x8;

    // Pre-allocate objects for layout measuring
    private Rect textBounds = new Rect();
    private Rect drawableBounds = new Rect();

    private int drawablesWidth = 0;
    private int drawablesHeight = 0;

    private short mPerformedOffset = 0;

    public CenteredDrawableTextView(Context context) {
        super(context);
        init(context, null);
    }

    public CenteredDrawableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CenteredDrawableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.CenteredDrawableTextView, 0, 0);

        drawablesWidth = typedArray.getDimensionPixelSize(
                R.styleable.CenteredDrawableTextView_compoundDrawableWidth, 0);
        drawablesHeight = typedArray.getDimensionPixelSize(
                R.styleable.CenteredDrawableTextView_compoundDrawableHeight, 0);

        typedArray.recycle();

        Drawable[] drawables = getCompoundDrawables();
        scale(drawables);
        setCompoundDrawables(drawables[LEFT], drawables[TOP], drawables[RIGHT], drawables[BOTTOM]);
    }

    private void scale(Drawable[] drawables) {
        if (drawablesWidth > 0 || drawablesHeight > 0) {
            for (Drawable drawable : drawables) {
                if (drawable == null) {
                    continue;
                }

                Rect realBounds = new Rect(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                float actualDrawableWidth = realBounds.width();
                float actualDrawableHeight = realBounds.height();
                float actualDrawableRatio = actualDrawableHeight / actualDrawableWidth;

                float scale = 0;
                // check if both width and height defined then adjust drawable size according to the ratio
                if (drawablesWidth > 0 || drawablesHeight > 0) {
                    //float placeholderRatio = drawablesHeight / (float) drawablesWidth;
                    //if (placeholderRatio > actualDrawableRatio) {
                    //    scale = drawablesWidth / actualDrawableWidth;
                    //} else {
                    //    scale = drawablesHeight / actualDrawableHeight;
                    //}
                    actualDrawableWidth = drawablesWidth;
                    actualDrawableHeight = drawablesHeight;
                } else if (drawablesHeight > 0) { // only height defined
                    scale = drawablesHeight / actualDrawableHeight;
                } else { // only width defined
                    scale = drawablesWidth / actualDrawableWidth;
                }

                if (scale != 0) {
                    actualDrawableWidth = actualDrawableWidth * scale;
                    actualDrawableHeight = actualDrawableHeight * scale;
                }

                realBounds.right = realBounds.left + Math.round(actualDrawableWidth);
                realBounds.bottom = realBounds.top + Math.round(actualDrawableHeight);

                drawable.setBounds(realBounds);
            }
        } else {
            for (Drawable drawable : drawables) {
                if (drawable == null) {
                    continue;
                }

                drawable.setBounds(new Rect(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()));
            }
        }
    }

    @Override
    public void setCompoundDrawables(@Nullable Drawable left, @Nullable Drawable top, @Nullable Drawable right, @Nullable Drawable bottom) {
        if (textBounds != null) {
            final CharSequence text = getText();
            if (!TextUtils.isEmpty(text)) {
                TextPaint textPaint = getPaint();
                textPaint.getTextBounds(text.toString(), 0, text.length(), textBounds);
            } else {
                textBounds.setEmpty();
            }

            final int width = getWidth() - (getPaddingLeft() + getPaddingRight());
            final int height = getHeight() - (getPaddingTop() + getPaddingBottom());

            if (left != null) {
                left.copyBounds(drawableBounds);
                int leftOffset = (width - textBounds.width()) / 2 - (drawableBounds.width() + getCompoundDrawablePadding());
                drawableBounds.offsetTo(0, 0);
                drawableBounds.offset(leftOffset, 0);
                left.setBounds(drawableBounds);
            }

            if (right != null) {
                right.copyBounds(drawableBounds);
                int rightOffset = (textBounds.width() - width) / 2 + (drawableBounds.width() + getCompoundDrawablePadding());
                drawableBounds.offsetTo(0, 0);
                drawableBounds.offset(rightOffset, 0);
                right.setBounds(drawableBounds);
            }
        }
        super.setCompoundDrawables(left, top, right, bottom);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (!changed) {
            return;
        }

        final CharSequence text = getText();
        if (!TextUtils.isEmpty(text)) {
            TextPaint textPaint = getPaint();
            textPaint.getTextBounds(text.toString(), 0, text.length(), textBounds);
        } else {
            textBounds.setEmpty();
        }

        final int width = getWidth() - (getPaddingLeft() + getPaddingRight());
        final int height = getHeight() - (getPaddingTop() + getPaddingBottom());

        final Drawable[] drawables = getCompoundDrawables();

        if (drawables[LEFT] != null && (mPerformedOffset & PERFORMED_LEFT_OFFSET) == 0) {
            drawables[LEFT].copyBounds(drawableBounds);
            int leftOffset = (width - textBounds.width()) / 2 - (drawableBounds.width() + getCompoundDrawablePadding());
            drawableBounds.offsetTo(0, 0);
            drawableBounds.offset(leftOffset, 0);
            drawables[LEFT].setBounds(drawableBounds);
            mPerformedOffset |= PERFORMED_LEFT_OFFSET;
        }

        if (drawables[RIGHT] != null && (mPerformedOffset & PERFORMED_RIGHT_OFFSET) == 0) {
            drawables[RIGHT].copyBounds(drawableBounds);
            int rightOffset = (textBounds.width() - width) / 2 + (drawableBounds.width() + getCompoundDrawablePadding());
            drawableBounds.offsetTo(0, 0);
            drawableBounds.offset(rightOffset, 0);
            drawables[RIGHT].setBounds(drawableBounds);
            mPerformedOffset |= PERFORMED_RIGHT_OFFSET;
        }

        if (drawables[TOP] != null && (mPerformedOffset & PERFORMED_TOP_OFFSET) == 0) {
            drawables[TOP].copyBounds(drawableBounds);
            int topOffset = (height - textBounds.height()) / 2 - (drawableBounds.height() + getCompoundDrawablePadding()) / 2;
            drawableBounds.offset(0, topOffset);
            drawables[TOP].setBounds(drawableBounds);
            mPerformedOffset |= PERFORMED_TOP_OFFSET;
        }

        if (drawables[BOTTOM] != null && (mPerformedOffset & PERFORMED_BOTTOM_OFFSET) == 0) {
            drawables[BOTTOM].copyBounds(drawableBounds);
            int bottomOffset = (textBounds.height() - height) / 2 + (drawableBounds.height() + getCompoundDrawablePadding());
            drawableBounds.offset(0, bottomOffset);
            drawables[BOTTOM].setBounds(drawableBounds);
            mPerformedOffset |= PERFORMED_BOTTOM_OFFSET;
        }
    }
}
