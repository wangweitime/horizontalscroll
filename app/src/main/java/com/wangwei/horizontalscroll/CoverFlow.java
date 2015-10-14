package com.wangwei.horizontalscroll;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Adapter;
import android.widget.AdapterView;



public class CoverFlow extends AdapterView<Adapter> implements GestureDetector.OnGestureListener {

    private static final String TAG = "CoverFlow";

    private static final boolean localLOGV = false;

    /**
     * Duration in milliseconds from the start of a scroll during which we're
     * unsure whether the user is scrolling or flinging.
     */
    private static final int SCROLL_TO_FLING_UNCERTAINTY_TIMEOUT = 250;

    /**
     * Horizontal spacing between items.
     */
    private int mSpacing = 0;

    /**
     * How long the transition animation should run when a child view changes
     * position, measured in milliseconds.
     */
    private int mAnimationDuration = 400;

    /**
     * The alpha of items that are not selected.
     */
    private float mUnselectedAlpha = 0.5f;
    
    /**
     * Left most edge of a child seen so far during layout.
     */
    private int mLeftMost;

    /**
     * Right most edge of a child seen so far during layout.
     */
    private int mRightMost;

    private int mGravity = Gravity.CENTER_VERTICAL;

    /**
     * Helper for detecting touch gestures.
     */
    private GestureDetector mGestureDetector;

    /**
     * The position of the item that received the user's down touch.
     */
    private int mDownTouchPosition;

    /**
     * The view of the item that received the user's down touch.
     */
    private View mDownTouchView;
    
    /**
     * Executes the delta scrolls from a fling or scroll movement. 
     */
    private FlingRunnable mFlingRunnable = new FlingRunnable();

    /**
     * Sets mSuppressSelectionChanged = false. This is used to set it to false
     * in the future. It will also trigger a selection changed.
     */
    private Runnable mDisableSuppressSelectionChangedRunnable = new Runnable() {
        @Override
        public void run() {
            mSuppressSelectionChanged = false;
            selectionChanged();
        }
    };
    
    /**
     * When fling runnable runs, it resets this to false. Any method along the
     * path until the end of its run() can set this to true to abort any
     * remaining fling. For example, if we've reached either the leftmost or
     * rightmost item, we will set this to true.
     */
    private boolean mShouldStopFling;
    
    /**
     * The currently selected item's child.
     */
    private View mSelectedChild;
    
    /**
     * Whether to continuously callback on the item selected listener during a
     * fling.
     */
    private boolean mShouldCallbackDuringFling = true;

    /**
     * Whether to callback when an item that is not selected is clicked.
     */
    private boolean mShouldCallbackOnUnselectedItemClick = true;

    /**
     * If true, do not callback to item selected listener. 
     */
    private boolean mSuppressSelectionChanged;

    /**
     * If true, we have received the "invoke" (center or enter buttons) key
     * down. This is checked before we action on the "invoke" key up, and is
     * subsequently cleared.
     */
    private boolean mReceivedInvokeKeyDown;
    
    private AdapterContextMenuInfo mContextMenuInfo;

    /**
     * If true, this onScroll is the first for this user's drag (remember, a
     * drag sends many onScrolls).
     */
    private boolean mIsFirstScroll;

    /**
     * If true, mFirstPosition is the position of the rightmost child, and
     * the children are ordered right to left.
     */
    private boolean mIsRtl = true;
    
    /**
     * Offset between the center of the selected child view and the center of the Gallery.
     * Used to reset position correctly during layout.
     */
    private int mSelectedCenterOffset;
    
    final RecycleBin mRecycler = new RecycleBin();
    
    int mFirstPosition = 0;
    
    Adapter mAdapter;
    
    /**
     * The last selected position we used when notifying
     */
    int mOldSelectedPosition = INVALID_POSITION;
    
    /**
     * The id of the last selected position we used when notifying
     */
    long mOldSelectedRowId = INVALID_ROW_ID;
    
    
    /**
     * The position within the adapter's data set of the item to select
     * during the next layout.
     */

    int mNextSelectedPosition = INVALID_POSITION;

    /**
     * The item id of the item to select during the next layout.
     */
    long mNextSelectedRowId = INVALID_ROW_ID;

    /**
     * The position within the adapter's data set of the currently selected item.
     */
    int mSelectedPosition = INVALID_POSITION;

    /**
     * The item id of the currently selected item.
     */
    long mSelectedRowId = INVALID_ROW_ID;
    
    // Only measure this many items to get a decent max width.
    private static final int MAX_ITEMS_MEASURED = 15;
    
    private Rect mTempRect = new Rect();
    

    int mHeightMeasureSpec;
    int mWidthMeasureSpec;
    
    private boolean mKeyDown = false;
    
    int mDeltaDistance = 0;
    
    ObjectAnimator mScrollToNextAnim;
    ObjectAnimator mScrollLongAnim;
    AnimatorSet mScrollToNextSet;
    
    private static final float SELECTED_SCALE = 1.2f;
    
    public CoverFlow(Context context) {
        this(context, null);
    }

    public CoverFlow(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setIsLongpressEnabled(true);
        

        // We draw the selected item last (because otherwise the item to theo
        // right overlaps it)
        setChildrenDrawingOrderEnabled(true);
        
        setStaticTransformationsEnabled(true);
    }

    /**
     * Whether or not to callback on any {@link #getOnItemSelectedListener()}
     * while the items are being flinged. If false, only the final selected item
     * will cause the callback. If true, all items between the first and the
     * final will cause callbacks.
     * 
     * @param shouldCallback Whether or not to callback on the listener while
     *            the items are being flinged.
     */
    public void setCallbackDuringFling(boolean shouldCallback) {
        mShouldCallbackDuringFling = shouldCallback;
    }

    /**
     * Whether or not to callback when an item that is not selected is clicked.
     * If false, the item will become selected (and re-centered). If true, the
     * {@link #getOnItemClickListener()} will get the callback.
     * 
     * @param shouldCallback Whether or not to callback on the listener when a
     *            item that is not selected is clicked.
     * @hide
     */
    public void setCallbackOnUnselectedItemClick(boolean shouldCallback) {
        mShouldCallbackOnUnselectedItemClick = shouldCallback;
    }
    
    /**
     * Sets how long the transition animation should run when a child view
     * changes position. Only relevant if animation is turned on.
     * 
     * @param animationDurationMillis The duration of the transition, in
     *        milliseconds.
     * 
     * @attr ref android.R.styleable#Gallery_animationDuration
     */
    public void setAnimationDuration(int animationDurationMillis) {
        mAnimationDuration = animationDurationMillis;
    }

    /**
     * Sets the spacing between items in a Gallery
     * 
     * @param spacing The spacing in pixels between items in the Gallery
     * 
     * @attr ref android.R.styleable#Gallery_spacing
     */
    public void setSpacing(int spacing) {
        mSpacing = spacing;
    }

    /**
     * Sets the alpha of items that are not selected in the Gallery.
     * 
     * @param unselectedAlpha the alpha for the items that are not selected.
     * 
     * @attr ref android.R.styleable#Gallery_unselectedAlpha
     */
    public void setUnselectedAlpha(float unselectedAlpha) {
        mUnselectedAlpha = unselectedAlpha;
    }

    /*@Override
    protected boolean getChildStaticTransformation(View child, Transformation t) {
        
        t.clear();
        t.setAlpha(child == mSelectedChild ? 1.0f : mUnselectedAlpha);
        
        return true;
    }*/

    @Override
    protected int computeHorizontalScrollExtent() {
        // Only 1 item is considered to be selected
        return 1;
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        // Current scroll position is the same as the selected position
        return mSelectedPosition;
    }

    @Override
    protected int computeHorizontalScrollRange() {
        // Scroll range is the same as the item count
        return mAdapter.getCount();
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        /*
         * Gallery expects Gallery.LayoutParams.
         */
        return new CoverFlow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        
        /*
         * Remember that we are in layout to prevent more layout request from
         * being generated.
         */
        //mInLayout = true;
        layout(0, false);
        //mInLayout = false;
    }


    /**
     * Tracks a motion scroll. In reality, this is used to do just about any
     * movement to items (touch scroll, arrow-key scroll, set an item as selected).
     * 
     * @param deltaX Change in X from the previous event.
     */
    void trackMotionScroll(int deltaX) {

        if (getChildCount() == 0) {
            return;
        }
        
        boolean toLeft = deltaX < 0; 
        
        int limitedDeltaX = getLimitedMotionScrollAmount(toLeft, deltaX);
        if (limitedDeltaX != deltaX) {
            // The above call returned a limited amount, so stop any scrolls/flings
            mFlingRunnable.endFling(false);
            onFinishedMovement();
        }
        
        offsetChildrenLeftAndRight(limitedDeltaX);
        
        detachOffScreenChildren(toLeft);
        
        if (toLeft) {
            // If moved left, there will be empty space on the right
            fillToGalleryRight();
        } else {
            // Similarly, empty space on the left
            fillToGalleryLeft();
        }
        
        // Clear unused views
        mRecycler.clear();
        
        setSelectionToCenterChild();

        updateStateOfSelectedView(true);
        final View selChild = mSelectedChild;
        if (selChild != null) {
            final int childLeft = selChild.getLeft();
            final int childCenter = selChild.getWidth() / 2;
            final int galleryCenter = getWidth() / 2;
            mSelectedCenterOffset = childLeft + childCenter - galleryCenter;
        }

        //onScrollChanged(0, 0, 0, 0); // dummy values, View's implementation does not use these.

        invalidate();
    }

    int getLimitedMotionScrollAmount(boolean motionToLeft, int deltaX) {
        int extremeItemPosition = motionToLeft != mIsRtl ? mAdapter.getCount() - 1 : 0;
        View extremeChild = getChildAt(extremeItemPosition - mFirstPosition);
        
        if (extremeChild == null) {
            return deltaX;
        }
        
        int extremeChildCenter = getCenterOfView(extremeChild);
        int galleryCenter = getCenterOfGallery();
        
        if (motionToLeft) {
            if (extremeChildCenter <= galleryCenter) {
                
                // The extreme child is past his boundary point!
                return 0;
            }
        } else {
            if (extremeChildCenter >= galleryCenter) {

                // The extreme child is past his boundary point!
                return 0;
            }
        }
        
        int centerDifference = galleryCenter - extremeChildCenter;

        return motionToLeft
                ? Math.max(centerDifference, deltaX)
                : Math.min(centerDifference, deltaX); 
    }

    /**
     * Offset the horizontal location of all children of this view by the
     * specified number of pixels.
     * 
     * @param offset the number of pixels to offset
     */
    private void offsetChildrenLeftAndRight(int offset) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            getChildAt(i).offsetLeftAndRight(offset);
        }
    }
    
    /**
     * @return The center of this Gallery.
     */
    private int getCenterOfGallery() {
        return (getWidth() - this.getPaddingLeft() - this.getPaddingRight()) / 2 + this.getPaddingLeft();
    }
    
    /**
     * @return The center of the given view.
     */
    private static int getCenterOfView(View view) {
        return view.getLeft() + view.getWidth() / 2;
    }
    
    /**
     * Detaches children that are off the screen (i.e.: Gallery bounds).
     * 
     * @param toLeft Whether to detach children to the left of the Gallery, or
     *            to the right.
     */
    private void detachOffScreenChildren(boolean toLeft) {
        int numChildren = getChildCount();
        int firstPosition = mFirstPosition;
        int start = 0;
        int count = 0;

        if (toLeft) {
            final int galleryLeft = getPaddingLeft();
            for (int i = 0; i < numChildren; i++) {
                int n = mIsRtl ? (numChildren - 1 - i) : i;
                final View child = getChildAt(n);
                if (child.getRight() >= galleryLeft) {
                    break;
                } else {
                    start = n;
                    count++;
                    mRecycler.put(firstPosition + n, child);
                }
            }
            if (!mIsRtl) {
                start = 0;
            }
        } else {
            final int galleryRight = getWidth() - getPaddingRight();
            for (int i = numChildren - 1; i >= 0; i--) {
                int n = mIsRtl ? numChildren - 1 - i : i;
                final View child = getChildAt(n);
                if (child.getLeft() <= galleryRight) {
                    break;
                } else {
                    start = n;
                    count++;
                    mRecycler.put(firstPosition + n, child);
                }
            }
            if (mIsRtl) {
                start = 0;
            }
        }

        detachViewsFromParent(start, count);
        
        if (toLeft != mIsRtl) {
            mFirstPosition += count;
        }
    }
    
    /**
     * Scrolls the items so that the selected item is in its 'slot' (its center
     * is the gallery's center).
     */
    private void scrollIntoSlots() {
        
        if (getChildCount() == 0 || mSelectedChild == null) return;
        
        int selectedCenter = getCenterOfView(mSelectedChild);
        int targetCenter = getCenterOfGallery();
        
        int scrollAmount = targetCenter - selectedCenter;
        if (scrollAmount != 0) {
            mFlingRunnable.startUsingDistance(scrollAmount);
        } else {
            onFinishedMovement();
        }
    }

    private void onFinishedMovement() {
        if (mSuppressSelectionChanged) {
            mSuppressSelectionChanged = false;
            
            // We haven't been callbacking during the fling, so do it now
            //super.selectionChanged();
        }
        mSelectedCenterOffset = 0;
        invalidate();
    }

    void selectionChanged() {
        if (!mSuppressSelectionChanged) {
            //super.selectionChanged();
        }
    }

    /**
     * Looks for the child that is closest to the center and sets it as the
     * selected child.
     */
    private void setSelectionToCenterChild() {
        
        View selView = mSelectedChild;
        if (mSelectedChild == null) return;
        
        int galleryCenter = getCenterOfGallery();
        
        // Common case where the current selected position is correct
        if (selView.getLeft() <= galleryCenter && selView.getRight() >= galleryCenter) {
            return;
        }
        
        // TODO better search
        int closestEdgeDistance = Integer.MAX_VALUE;
        int newSelectedChildIndex = 0;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            
            View child = getChildAt(i);
            
            if (child.getLeft() <= galleryCenter && child.getRight() >=  galleryCenter) {
                // This child is in the center
                newSelectedChildIndex = i;
                break;
            }
            
            int childClosestEdgeDistance = Math.min(Math.abs(child.getLeft() - galleryCenter),
                    Math.abs(child.getRight() - galleryCenter));
            if (childClosestEdgeDistance < closestEdgeDistance) {
                closestEdgeDistance = childClosestEdgeDistance;
                newSelectedChildIndex = i;
            }
        }
        
        int newPos = mFirstPosition + newSelectedChildIndex;
        
        if (newPos != mSelectedPosition) {
            setSelectedPositionInt(newPos);
            setNextSelectedPositionInt(newPos);
            checkSelectionChanged();
        }
    }

    /**
     * Creates and positions all views for this Gallery.
     * <p>
     * We layout rarely, most of the time {@link #trackMotionScroll(int)} takes
     * care of repositioning, adding, and removing children.
     * 
     * @param delta Change in the selected position. +1 means the selection is
     *            moving to the right, so views are scrolling to the left. -1
     *            means the selection is moving to the left.
     */

    void layout(int delta, boolean animate) {

        mIsRtl = false;

        int childrenLeft = 0;
        int childrenWidth = getRight() - getLeft();

        /*if (mDataChanged) {
            handleDataChanged();
        }*/

        // Handle an empty gallery by removing all views.
        if (mAdapter.getCount() == 0) {
            resetList();
            return;
        }

        // Update to the new selected position.
        /*if (mNextSelectedPosition >= 0) {
            setSelectedPositionInt(mNextSelectedPosition);
        }*/

        // All views go in recycler while we are in layout
        recycleAllViews();

        // Clear out old views
        //removeAllViewsInLayout();
        detachAllViewsFromParent();

        /*
         * These will be used to give initial positions to views entering the
         * gallery as we scroll
         */
        mRightMost = 0;
        mLeftMost = 0;

        // Make selected view and center it
        
        /*
         * mFirstPosition will be decreased as we add views to the left later
         * on. The 0 for x will be offset in a couple lines down.
         */  
        mFirstPosition = mSelectedPosition;
        View sel = makeAndAddView(mSelectedPosition, 0, 0, true);
        sel.setScaleX(SELECTED_SCALE);
        sel.setScaleY(SELECTED_SCALE);
        sel.setAlpha(1);
        // Put the selected child in the center
        int selectedOffset = childrenLeft + (childrenWidth / 2) - (sel.getWidth() / 2) +
                mSelectedCenterOffset;
        sel.offsetLeftAndRight(selectedOffset);

        
        fillToGalleryRight();       
        fillToGalleryLeft();
     
        // Flush any cached views that did not get reused above
        mRecycler.clear();

        invalidate();
        checkSelectionChanged();

        //mDataChanged = false;
        //mNeedSync = false;
        setNextSelectedPositionInt(mSelectedPosition);
        
        updateSelectedItemMetadata();
    }

    private void fillToGalleryLeft() {
        if (mIsRtl) {
            fillToGalleryLeftRtl();
        } else {
            fillToGalleryLeftLtr();
        }
    }

    private void fillToGalleryLeftRtl() {
        int itemSpacing = mSpacing;
        int galleryLeft = getPaddingLeft();
        int numChildren = getChildCount();
        int numItems = mAdapter.getCount();

        // Set state for initial iteration
        View prevIterationView = getChildAt(numChildren - 1);
        int curPosition;
        int curRightEdge;

        if (prevIterationView != null) {
            curPosition = mFirstPosition + numChildren;
            curRightEdge = prevIterationView.getLeft() - itemSpacing;
        } else {
            // No children available!
            mFirstPosition = curPosition = mAdapter.getCount() - 1;
            curRightEdge = getRight() - getLeft() - getPaddingRight();
            mShouldStopFling = true;
        }

        while (curRightEdge > galleryLeft && curPosition < mAdapter.getCount()) {
            prevIterationView = makeAndAddView(curPosition, curPosition - mSelectedPosition,
                    curRightEdge, false);

            // Set state for next iteration
            curRightEdge = prevIterationView.getLeft() - itemSpacing;
            curPosition++;
        }
    }

    private void fillToGalleryLeftLtr() {
        int itemSpacing = mSpacing;
        int galleryLeft = this.getPaddingLeft();
        
        // Set state for initial iteration
        View prevIterationView = getChildAt(0);
        int curPosition;
        int curRightEdge;
        
        if (prevIterationView != null) {
            curPosition = mFirstPosition - 1;
            curRightEdge = prevIterationView.getLeft() - itemSpacing;
        } else {
            // No children available!
            curPosition = 0; 
            curRightEdge = getRight() - getLeft() - getPaddingRight();
            mShouldStopFling = true;
        }
                
        while (curRightEdge > galleryLeft && curPosition >= 0) {
            prevIterationView = makeAndAddView(curPosition, curPosition - mSelectedPosition,
                    curRightEdge, false);

            // Remember some state
            mFirstPosition = curPosition;
            
            // Set state for next iteration
            curRightEdge = prevIterationView.getLeft() - itemSpacing;
            curPosition--;
        }
    }
    
    private void fillToGalleryRight() {
        if (mIsRtl) {
            fillToGalleryRightRtl();
        } else {
            fillToGalleryRightLtr();
        }
    }

    private void fillToGalleryRightRtl() {
        int itemSpacing = mSpacing;
        int galleryRight = getRight() - getLeft() - getPaddingRight();

        // Set state for initial iteration
        View prevIterationView = getChildAt(0);
        int curPosition;
        int curLeftEdge;

        if (prevIterationView != null) {
            curPosition = mFirstPosition -1;
            curLeftEdge = prevIterationView.getRight() + itemSpacing;
        } else {
            curPosition = 0;
            curLeftEdge = getPaddingLeft();
            mShouldStopFling = true;
        }

        while (curLeftEdge < galleryRight && curPosition >= 0) {
            prevIterationView = makeAndAddView(curPosition, curPosition - mSelectedPosition,
                    curLeftEdge, true);

            // Remember some state
            mFirstPosition = curPosition;

            // Set state for next iteration
            curLeftEdge = prevIterationView.getRight() + itemSpacing;
            curPosition--;
        }
    }

    private void fillToGalleryRightLtr() {
        int itemSpacing = mSpacing;
        int galleryRight = getRight() - getLeft() - getPaddingRight();
        int numChildren = getChildCount();
        int numItems = mAdapter.getCount();
        
        // Set state for initial iteration
        View prevIterationView = getChildAt(numChildren - 1);
        int curPosition;
        int curLeftEdge;
        
        if (prevIterationView != null) {
            curPosition = mFirstPosition + numChildren;
            curLeftEdge = prevIterationView.getRight() + itemSpacing;
        } else {
            mFirstPosition = curPosition = mAdapter.getCount() - 1;
            curLeftEdge = getPaddingLeft();
            mShouldStopFling = true;
        }
                
        while (curLeftEdge < galleryRight && curPosition < numItems) {
            prevIterationView = makeAndAddView(curPosition, curPosition - mSelectedPosition,
                    curLeftEdge, true);

            // Set state for next iteration
            curLeftEdge = prevIterationView.getRight() + itemSpacing;
            curPosition++;
        }
    }

    /**
     * Obtain a view, either by pulling an existing view from the recycler or by
     * getting a new one from the adapter. If we are animating, make sure there
     * is enough information in the view's layout parameters to animate from the
     * old to new positions.
     * 
     * @param position Position in the gallery for the view to obtain
     * @param offset Offset from the selected position
     * @param x X-coordinate indicating where this view should be placed. This
     *        will either be the left or right edge of the view, depending on
     *        the fromLeft parameter
     * @param fromLeft Are we positioning views based on the left edge? (i.e.,
     *        building from left to right)?
     * @return A view that has been added to the gallery
     */
    private View makeAndAddView(int position, int offset, int x, boolean fromLeft) {

        View child;
        /*if (!mDataChanged)*/ {
            child = mRecycler.get(position);
            if (child != null) {
                // Can reuse an existing view
                int childLeft = child.getLeft();
                
                // Remember left and right edges of where views have been placed
                mRightMost = Math.max(mRightMost, childLeft 
                        + child.getMeasuredWidth());
                mLeftMost = Math.min(mLeftMost, childLeft);

                // Position the view
                setUpChild(child, offset, x, fromLeft);

                return child;
            }
        }

        // Nothing found in the recycler -- ask the adapter for a view
        child = getAdapter().getView(position, null, this);

        // Position the view
        setUpChild(child, offset, x, fromLeft);

        return child;
    }

    /**
     * Helper for makeAndAddView to set the position of a view and fill out its
     * layout parameters.
     * 
     * @param child The view to position
     * @param offset Offset from the selected position
     * @param x X-coordinate indicating where this view should be placed. This
     *        will either be the left or right edge of the view, depending on
     *        the fromLeft parameter
     * @param fromLeft Are we positioning views based on the left edge? (i.e.,
     *        building from left to right)?
     */
    private void setUpChild(View child, int offset, int x, boolean fromLeft) {

        // Respect layout params that are already in the view. Otherwise
        // make some up...
        CoverFlow.LayoutParams lp = (CoverFlow.LayoutParams) child.getLayoutParams();
        if (lp == null) {
            lp =  (CoverFlow.LayoutParams)generateDefaultLayoutParams();
        }
        


        addViewInLayout(child, fromLeft != mIsRtl ? -1 : 0, generateDefaultLayoutParams(), true);

        child.setSelected(offset == 0);

        // Get measure specs
        int childHeightSpec = ViewGroup.getChildMeasureSpec(mHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom(), lp.height);
        int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight(), lp.width);

        // Measure child
        child.measure(childWidthSpec, childHeightSpec);

        int childLeft;
        int childRight;

        // Position vertically based on gravity setting
        int childTop = calculateTop(child, true);
        int childBottom = childTop + child.getMeasuredHeight();

        int width = child.getMeasuredWidth();
        if (fromLeft) {
            childLeft = x;
            childRight = childLeft + width;
        } else {
            childLeft = x - width;
            childRight = x;
        }

        child.layout(childLeft, childTop, childRight, childBottom);
        child.setAlpha(mUnselectedAlpha);
    }

    /**
     * Figure out vertical placement based on mGravity
     * 
     * @param child Child to place
     * @return Where the top of the child should be
     */
    private int calculateTop(View child, boolean duringLayout) {
        int myHeight = duringLayout ? getMeasuredHeight() : getHeight();
        int childHeight = duringLayout ? child.getMeasuredHeight() : child.getHeight(); 
        
        int childTop = 0;

        switch (mGravity) {
        case Gravity.TOP:
            childTop = getPaddingTop();
            break;
        case Gravity.CENTER_VERTICAL:
            int availableSpace = myHeight - getPaddingBottom()
                    - getPaddingTop() - childHeight;
            childTop = getPaddingTop() + (availableSpace / 2);
            break;
        case Gravity.BOTTOM:
            childTop = myHeight - getPaddingBottom() - childHeight;
            break;
        }
        return childTop;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Give everything to the gesture detector
        boolean retValue = mGestureDetector.onTouchEvent(event);

        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP) {
            // Helper method for lifted finger
            onUp();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            onCancel();
        }
        
        return retValue;
    }
    
    @Override
    public boolean onSingleTapUp(MotionEvent e) {

        if (mDownTouchPosition >= 0) {
            
            // An item tap should make it selected, so scroll to this child.
            scrollToChild(mDownTouchPosition - mFirstPosition);

            // Also pass the click so the client knows, if it wants to.
            if (mShouldCallbackOnUnselectedItemClick || mDownTouchPosition == mSelectedPosition) {
                performItemClick(mDownTouchView, mDownTouchPosition, getAdapter()
                        .getItemId(mDownTouchPosition));
            }
            
            return true;
        }
        
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        
        if (!mShouldCallbackDuringFling) {
            // We want to suppress selection changes
            
            // Remove any future code to set mSuppressSelectionChanged = false
            removeCallbacks(mDisableSuppressSelectionChangedRunnable);

            // This will get reset once we scroll into slots
            if (!mSuppressSelectionChanged) mSuppressSelectionChanged = true;
        }
        
        // Fling the gallery!
        mFlingRunnable.startUsingVelocity((int) -velocityX);
        
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

        if (localLOGV) Log.v(TAG, String.valueOf(e2.getX() - e1.getX()));
        
        /*
         * Now's a good time to tell our parent to stop intercepting our events!
         * The user has moved more than the slop amount, since GestureDetector
         * ensures this before calling this method. Also, if a parent is more
         * interested in this touch's events than we are, it would have
         * intercepted them by now (for example, we can assume when a Gallery is
         * in the ListView, a vertical scroll would not end up in this method
         * since a ListView would have intercepted it by now).
         */
        //mParent.requestDisallowInterceptTouchEvent(true);
        
        // As the user scrolls, we want to callback selection changes so related-
        // info on the screen is up-to-date with the gallery's selection
        if (!mShouldCallbackDuringFling) {
            if (mIsFirstScroll) {
                /*
                 * We're not notifying the client of selection changes during
                 * the fling, and this scroll could possibly be a fling. Don't
                 * do selection changes until we're sure it is not a fling.
                 */
                if (!mSuppressSelectionChanged) mSuppressSelectionChanged = true;
                postDelayed(mDisableSuppressSelectionChangedRunnable, SCROLL_TO_FLING_UNCERTAINTY_TIMEOUT);
            }
        } else {
            if (mSuppressSelectionChanged) mSuppressSelectionChanged = false;
        }
        
        // Track the motion
        trackMotionScroll(-1 * (int) distanceX);
       
        mIsFirstScroll = false;
        return true;
    }
    
    @Override
    public boolean onDown(MotionEvent e) {

        // Kill any existing fling/scroll
        mFlingRunnable.stop(false);

        // Get the item's view that was touched
        //mDownTouchPosition = pointToPosition((int) e.getX(), (int) e.getY());
        
        if (mDownTouchPosition >= 0) {
            mDownTouchView = getChildAt(mDownTouchPosition - mFirstPosition);
            mDownTouchView.setPressed(true);
        }
        
        // Reset the multiple-scroll tracking state
        mIsFirstScroll = true;
        
        // Must return true to get matching events for this down event.
        return true;
    }

    /**
     * Called when a touch event's action is MotionEvent.ACTION_UP.
     */
    void onUp() {
        
        if (mFlingRunnable.mScroller.isFinished()) {
            scrollIntoSlots();
        }
        
        dispatchUnpress();
    }
    
    /**
     * Called when a touch event's action is MotionEvent.ACTION_CANCEL.
     */
    void onCancel() {
        onUp();
    }
    
    @Override
    public void onLongPress(MotionEvent e) {
        
        if (mDownTouchPosition < 0) {
            return;
        }
        
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        //long id = getItemIdAtPosition(mDownTouchPosition);
        //dispatchLongPress(mDownTouchView, mDownTouchPosition, id);
    }

    // Unused methods from GestureDetector.OnGestureListener below
    
    @Override
    public void onShowPress(MotionEvent e) {
    }

    // Unused methods from GestureDetector.OnGestureListener above
    
    private void dispatchPress(View child) {
        
        if (child != null) {
            child.setPressed(true);
        }
        
        setPressed(true);
    }
    
    private void dispatchUnpress() {
        
        for (int i = getChildCount() - 1; i >= 0; i--) {
            getChildAt(i).setPressed(false);
        }
        
        setPressed(false);
    }
    
    @Override
    public void dispatchSetSelected(boolean selected) {
        /*
         * We don't want to pass the selected state given from its parent to its
         * children since this widget itself has a selected state to give to its
         * children.
         */
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        
        // Show the pressed state on the selected child
        if (mSelectedChild != null) {
            mSelectedChild.setPressed(pressed);
        }
    }

    @Override
    protected ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }

/*    @Override
    public boolean showContextMenuForChild(View originalView) {

        final int longPressPosition = getPositionForView(originalView);
        if (longPressPosition < 0) {
            return false;
        }
        
        final long longPressId = mAdapter.getItemId(longPressPosition);
        return dispatchLongPress(originalView, longPressPosition, longPressId);
    }*/

 /*   @Override
    public boolean showContextMenu() {
        
        if (isPressed() && mSelectedPosition >= 0) {
            int index = mSelectedPosition - mFirstPosition;
            View v = getChildAt(index);
            return dispatchLongPress(v, mSelectedPosition, mSelectedRowId);
        }        
        
        return false;
    }*/

/*    private boolean dispatchLongPress(View view, int position, long id) {
        boolean handled = false;
        
        if (mOnItemLongClickListener != null) {
            handled = mOnItemLongClickListener.onItemLongClick(this, mDownTouchView,
                    mDownTouchPosition, id);
        }

        if (!handled) {
            mContextMenuInfo = new AdapterContextMenuInfo(view, position, id);
            handled = super.showContextMenuForChild(this);
        }

        if (handled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        
        return handled;
    }*/
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Gallery steals all key events
        return event.dispatch(this, null, null);
    }

    /**
     * Handles left, right, and clicking
     * @see android.view.View#onKeyDown
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            
        case KeyEvent.KEYCODE_DPAD_LEFT:
            if (!mKeyDown && movePrevious()) {
                playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                mKeyDown = true;
                return true;
            }else{
            	mFlingRunnable.startUsingLongVelocity(-2000);
            }
            break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            if (!mKeyDown && moveNext()) {
                playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                mKeyDown = true;
                return true;
            }else{
            	mFlingRunnable.startUsingLongVelocity(2000);
            }
            
            break;
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_ENTER:
            mReceivedInvokeKeyDown = true;
            // fallthrough to default handling
        }
        mKeyDown = true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
	        case KeyEvent.KEYCODE_DPAD_CENTER:
	        case KeyEvent.KEYCODE_ENTER: {
	            
	            if (mReceivedInvokeKeyDown) {
	                if (mAdapter.getCount() > 0) {
	    
	                    dispatchPress(mSelectedChild);
	                    postDelayed(new Runnable() {
	                        @Override
	                        public void run() {
	                            dispatchUnpress();
	                        }
	                    }, ViewConfiguration.getPressedStateDuration());
	    
	                    int selectedIndex = mSelectedPosition - mFirstPosition;
	                    performItemClick(getChildAt(selectedIndex), mSelectedPosition, getAdapter()// mAdapter
	                            .getItemId(mSelectedPosition));
	                }
	            }
	            
	            // Clear the flag
	            mReceivedInvokeKeyDown = false;
	            mKeyDown = false;
	            return true;
	        }
	        case KeyEvent.KEYCODE_DPAD_LEFT:
	        case KeyEvent.KEYCODE_DPAD_RIGHT:
	        {
	        	mFlingRunnable.stopLongScroll();
	        	mKeyDown = false;
	        }
        }
        
        return super.onKeyUp(keyCode, event);
    }
    
    boolean movePrevious() {
        if (mAdapter.getCount() > 0 && mSelectedPosition > 0) {
            scrollToChild(mSelectedPosition - mFirstPosition - 1);
            return true;
        } else {
            return false;
        }
    }

    boolean moveNext() {
        if (mAdapter.getCount() > 0 && mSelectedPosition < mAdapter.getCount() - 1) {
            scrollToChild(mSelectedPosition - mFirstPosition + 1);
            return true;
        } else {
            return false;
        }
    }

    private boolean scrollToChild(int childPosition) {
        View child = getChildAt(childPosition);
        
        if (child != null) {
            int distance = getCenterOfGallery() - getCenterOfView(child);
            mFlingRunnable.startUsingDistance(distance);
            return true;
        }
        
        return false;
    }
    
    //@Override
    void setSelectedPositionInt(int position) {
        //super.setSelectedPositionInt(position);
        mSelectedPosition = position;
        mSelectedRowId = getItemIdAtPosition(position);
        // Updates any metadata we keep about the selected item.
        updateSelectedItemMetadata();
    }
    
    /**
     * Utility to keep mNextSelectedPosition and mNextSelectedRowId in sync
     * @param position Intended value for mSelectedPosition the next time we go
     * through layout
     */
    void setNextSelectedPositionInt(int position) {
        mNextSelectedPosition = position;
        mNextSelectedRowId = getItemIdAtPosition(position);
        // If we are trying to sync to the selection, update that too
        /*if (mNeedSync && mSyncMode == SYNC_SELECTED_POSITION && position >= 0) {
            mSyncPosition = position;
            mSyncRowId = mNextSelectedRowId;
        }*/
    }

    private void updateSelectedItemMetadata() {
        
        View oldSelectedChild = mSelectedChild;

        View child = mSelectedChild = getChildAt(mSelectedPosition - mFirstPosition);
        if (child == null) {
            return;
        }

        child.setSelected(true);
        child.setFocusable(true);

        if (hasFocus()) {
            child.requestFocus();
        }

        // We unfocus the old child down here so the above hasFocus check
        // returns true
        if (oldSelectedChild != null && oldSelectedChild != child) {

            // Make sure its drawable state doesn't contain 'selected'
            oldSelectedChild.setSelected(false);
            
            // Make sure it is not focusable anymore, since otherwise arrow keys
            // can make this one be focused
            oldSelectedChild.setFocusable(false);
        }
        
    }
    
    /**
     * Describes how the child views are aligned.
     * @param gravity
     * 
     * @attr ref android.R.styleable#Gallery_gravity
     */
    public void setGravity(int gravity)
    {
        if (mGravity != gravity) {
            mGravity = gravity;
            requestLayout();
        }
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        int selectedIndex = mSelectedPosition - mFirstPosition;
        
        // Just to be safe
        if (selectedIndex < 0) return i;
        
        if (i == childCount - 1) {
            // Draw the selected child last
            return selectedIndex;
        } else if (i >= selectedIndex) {
            // Move the children after the selected child earlier one
            return i + 1;
        } else {
            // Keep the children before the selected child the same
            return i;
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        
        /*
         * The gallery shows focus by focusing the selected item. So, give
         * focus to our selected item instead. We steal keys from our
         * selected item elsewhere.
         */
        if (gainFocus && mSelectedChild != null) {
            mSelectedChild.requestFocus(direction);
            mSelectedChild.setSelected(true);
        }

    }

/*    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(Gallery.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Gallery.class.getName());
        info.setScrollable(getCount() > 1);
        if (isEnabled()) {
            if (getCount() > 0 && mSelectedPosition < getCount() - 1) {
                info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            }
            if (isEnabled() && getCount() > 0 && mSelectedPosition > 0) {
                info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            }
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                if (isEnabled() && getCount() > 0 && mSelectedPosition < getCount() - 1) {
                    final int currentChildIndex = mSelectedPosition - mFirstPosition;
                    return scrollToChild(currentChildIndex + 1);
                }
            } return false;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                if (isEnabled() && getCount() > 0 && mSelectedPosition > 0) {
                    final int currentChildIndex = mSelectedPosition - mFirstPosition;
                    return scrollToChild(currentChildIndex - 1);
                }
            } return false;
        }
        return false;
    }*/

    /**
     * Responsible for fling behavior. Use {@link #startUsingVelocity(int)} to
     * initiate a fling. Each frame of the fling is handled in {@link #run()}.
     * A FlingRunnable will keep re-posting itself until the fling is done.
     */
    private class FlingRunnable implements Runnable {
        /**
         * Tracks the decay of a fling scroll
         */
        private Scroller mScroller;

        /**
         * X value reported by mScroller on the previous fling
         */
        private int mLastFlingX;

        public FlingRunnable() {
        	
        	mScroller = new Scroller(getContext(),new DecelerateInterpolator());
            //mScroller = new Scroller(getContext(),new AccelerateInterpolator());
        }

        private void startCommon() {
            // Remove any pending flings
            removeCallbacks(this);
        }
        
        public void startUsingVelocity(int initialVelocity) {
            if (initialVelocity == 0) return;
            
            startCommon();
            
            int initialX = initialVelocity < 0 ? Integer.MAX_VALUE : 0;
            mLastFlingX = initialX;
            mScroller.fling(initialX, 0, initialVelocity, 0,
                    0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
            post(this);
        }

        public void startUsingDistance(int distance) {
            if (distance == 0) return;
            
            startCommon();
            
            mLastFlingX = 0;
            mScroller.startScroll(0, 0, -distance, 0, mAnimationDuration);
            post(this);
        }
        
        public void startUsingLongVelocity(int initialVelocity) {
        	if(mScroller.isLongScroll()) return;
            if (initialVelocity == 0) return;
            startCommon();
            
            mScroller.longScroll(initialVelocity, 0,initialVelocity/2,0,
            		Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            post(this);
        }
        
        public void stopLongScroll(){
        	if(!mScroller.isLongScroll()) return;
            removeCallbacks(this);
            mScroller.forceFinished(true);
            scrollIntoSlots();
            //if (scrollIntoSlots) scrollIntoSlots();
        }
        
        public void stop(boolean scrollIntoSlots) {
            removeCallbacks(this);
            endFling(scrollIntoSlots);
        }
        
        private void endFling(boolean scrollIntoSlots) {
            /*
             * Force the scroller's status to finished (without setting its
             * position to the end)
             */
            mScroller.forceFinished(true);
            
            if (scrollIntoSlots) scrollIntoSlots();
        }

        @Override
        public void run() {

            if (mAdapter.getCount() == 0) {
                endFling(true);
                return;
            }

            mShouldStopFling = false;
            
            final Scroller scroller = mScroller;
            boolean more = scroller.computeScrollOffset();
            final int x = scroller.getCurrX();

            // Flip sign to convert finger direction to list items direction
            // (e.g. finger moving down means list is moving towards the top)
            int delta = mLastFlingX - x;

            // Pretend that each frame of a fling scroll is a touch scroll
            if (delta > 0) {
                // Moving towards the left. Use leftmost view as mDownTouchPosition
                mDownTouchPosition = mIsRtl ? (mFirstPosition + getChildCount() - 1) :
                    mFirstPosition;

                // Don't fling more than 1 screen
                delta = Math.min(getWidth() - getPaddingLeft() - getPaddingRight() - 1, delta);
            } else {
                // Moving towards the right. Use rightmost view as mDownTouchPosition
                int offsetToLast = getChildCount() - 1;
                mDownTouchPosition = mIsRtl ? mFirstPosition :
                    (mFirstPosition + getChildCount() - 1);

                // Don't fling more than 1 screen
                delta = Math.max(-(getWidth() - getPaddingRight() - getPaddingLeft() - 1), delta);
            }

            trackMotionScroll(delta);

            if (more && !mShouldStopFling) {
                mLastFlingX = x;
                post(this);
            } else {
               endFling(true);
            }
        }
        
    }
    
    /**
     * Gallery extends LayoutParams to provide a place to hold current
     * Transformation information along with previous position/transformation
     * info.
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }
    
/*    void handleDataChanged() {
        final int count = getCount();
        boolean found = false;

        if (count > 0) {

            int newPos;

            // Find the row we are supposed to sync to
            if (mNeedSync) {
                // Update this first, since setNextSelectedPositionInt inspects
                // it
                mNeedSync = false;

                // See if we can find a position in the new data with the same
                // id as the old selection
                newPos = findSyncPosition();
                if (newPos >= 0) {
                    // Verify that new selection is selectable
                    int selectablePos = lookForSelectablePosition(newPos, true);
                    if (selectablePos == newPos) {
                        // Same row id is selected
                        setNextSelectedPositionInt(newPos);
                        found = true;
                    }
                }
            }
            if (!found) {
                // Try to use the same position if we can't find matching data
                newPos = getSelectedItemPosition();

                // Pin position to the available range
                if (newPos >= count) {
                    newPos = count - 1;
                }
                if (newPos < 0) {
                    newPos = 0;
                }

                // Make sure we select something selectable -- first look down
                int selectablePos = lookForSelectablePosition(newPos, true);
                if (selectablePos < 0) {
                    // Looking down didn't work -- try looking up
                    selectablePos = lookForSelectablePosition(newPos, false);
                }
                if (selectablePos >= 0) {
                    setNextSelectedPositionInt(selectablePos);
                    checkSelectionChanged();
                    found = true;
                }
            }
        }
        if (!found) {
            // Nothing is selected
            mSelectedPosition = INVALID_POSITION;
            mSelectedRowId = INVALID_ROW_ID;
            mNextSelectedPosition = INVALID_POSITION;
            mNextSelectedRowId = INVALID_ROW_ID;
            mNeedSync = false;
            checkSelectionChanged();
        }

        //TODO: Hmm, we do not know the old state so this is sub-optimal
        notifyAccessibilityStateChanged();
    }*/
    
    class RecycleBin {
        private final SparseArray<View> mScrapHeap = new SparseArray<View>();

        public void put(int position, View v) {
            mScrapHeap.put(position, v);
        }
        
        View get(int position) {
            // System.out.print("Looking for " + position);
            View result = mScrapHeap.get(position);
            if (result != null) {
                // System.out.println(" HIT");
                mScrapHeap.delete(position);
            } else {
                // System.out.println(" MISS");
            }
            return result;
        }

        void clear() {
            final SparseArray<View> scrapHeap = mScrapHeap;
            final int count = scrapHeap.size();
            for (int i = 0; i < count; i++) {
                final View view = scrapHeap.valueAt(i);
                if (view != null) {
                    removeDetachedView(view, true);
                }
            }
            scrapHeap.clear();
        }
    }
    
    void resetList() {
        //mDataChanged = false;
       // mNeedSync = false;
        
        removeAllViewsInLayout();
        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;
        
        setSelectedPositionInt(INVALID_POSITION);
        setNextSelectedPositionInt(INVALID_POSITION);
        invalidate();
    }
    
    void recycleAllViews() {
        final int childCount = getChildCount();
        final RecycleBin recycleBin = mRecycler;
        final int position = mFirstPosition;

        // All views go in recycler
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            int index = position + i;
            recycleBin.put(index, v);
        }  
    }
    
    @Override
    public View getSelectedView() {
        if (mAdapter.getCount() > 0 && mSelectedPosition >= 0) {
            return getChildAt(mSelectedPosition - mFirstPosition);
        } else {
            return null;
        }
    }
    
    @Override
    public Adapter getAdapter() {
        return mAdapter;
    }
    
    @Override
    public void setAdapter(final Adapter adapter) {
        if (null != mAdapter) {
            resetList();
        }
        
        mAdapter = adapter;
        
        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;
        
        if (mAdapter != null) {
            int position = mAdapter.getCount() > 0 ? 0 : INVALID_POSITION;
            setSelectedPositionInt(position);
            setNextSelectedPositionInt(position);                     
        } else {       
            resetList();
            // Nothing selected
            checkSelectionChanged();
        }

        requestLayout();
    }
    
    @Override
    public void setSelection(final int position) {
    	mSelectedPosition = position;
    }

    int getChildHeight(View child) {
        return child.getMeasuredHeight();
    }
    
    int getChildWidth(View child) {
        return child.getMeasuredWidth();
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize;
        int heightSize;


     /*   if (mDataChanged) {
            handleDataChanged();
        }*/
        
        int preferredHeight = 0;
        int preferredWidth = 0;
        boolean needsMeasuring = true;
        
        int selectedPosition = mSelectedPosition;
        if (selectedPosition >= 0 && mAdapter != null && selectedPosition < mAdapter.getCount()) {
            // Try looking in the recycler. (Maybe we were measured once already)
            View view = mRecycler.get(selectedPosition);
            if (view == null) {
                // Make a new one
                view = mAdapter.getView(selectedPosition, null, this);

                //if (view.getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                //    view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
                //}
            }

            if (view != null) {
                // Put in recycler for re-measuring and/or layout
                mRecycler.put(selectedPosition, view);

                if (view.getLayoutParams() == null) {
                    //mBlockLayoutRequests = true;
                    view.setLayoutParams(generateDefaultLayoutParams());
                    //mBlockLayoutRequests = false;
                }
                measureChild(view, widthMeasureSpec, heightMeasureSpec);
                
                preferredHeight = getChildHeight(view) + getPaddingTop() + getPaddingBottom();
                preferredWidth = getChildWidth(view) + getPaddingLeft() + getPaddingRight();
                
                needsMeasuring = false;
            }
        }
        
        if (needsMeasuring) {
            // No views -- just use padding
            preferredHeight = getPaddingTop() + getPaddingBottom();
            if (widthMode == MeasureSpec.UNSPECIFIED) {
                preferredWidth = getPaddingLeft() + getPaddingRight();
            }
        }

        preferredHeight = Math.max((int)(preferredHeight*SELECTED_SCALE), getSuggestedMinimumHeight());
        preferredWidth = Math.max(preferredWidth, getSuggestedMinimumWidth());

        heightSize = resolveSizeAndState(preferredHeight, heightMeasureSpec, 0);
        widthSize = resolveSizeAndState(preferredWidth, widthMeasureSpec, 0);

        setMeasuredDimension(widthSize, heightSize);
        mHeightMeasureSpec = heightMeasureSpec;
        mWidthMeasureSpec = widthMeasureSpec;
    }
    
    void checkSelectionChanged() {
        if ((mSelectedPosition != mOldSelectedPosition) || (mSelectedRowId != mOldSelectedRowId)) {
            selectionChanged();
            mOldSelectedPosition = mSelectedPosition;
            mOldSelectedRowId = mSelectedRowId;
        }
    }
    
    
    void setDeltaDistance(int delta){
    	mDeltaDistance = delta;
    }
    
    void updateStateOfSelectedView(boolean toleft){
    	
    	Log.d(TAG,"updateStateOfSelectedView mSelectedPosition: "+mSelectedPosition);
    	for(int i=mSelectedPosition-1;i<=mSelectedPosition+1;++i){
    		if ((i - mFirstPosition) < mAdapter.getCount() && (i - mFirstPosition) >= 0) {
    			View view = getChildAt(i - mFirstPosition);
    			int viewcenter = getCenterOfView(view);
    			if(viewcenter < getCenterOfGallery()){
    				float rate = 1.0f;
    				//if(toleft){
    					View tempview = getChildAt(i - mFirstPosition +1);
    					rate = 1.0f-(float)(getCenterOfGallery() - viewcenter)/(getCenterOfView(tempview)-viewcenter);
    				//}
    				if(rate < 0 ) rate = 0;
    				if(rate > 1.0f) rate = 1;
    				view.setScaleX(1+(SELECTED_SCALE-1)*rate);
    				view.setScaleY(1+(SELECTED_SCALE-1)*rate);
    				view.setAlpha(mUnselectedAlpha+(1-mUnselectedAlpha)*rate);
    				Log.d(TAG, "left view.Scale "+i+" "+view.getScaleX());
    			}else if(viewcenter > getCenterOfGallery()){
    				float rate = 1.0f;
    				//if(toleft){
    					View tempview = getChildAt(i - mFirstPosition -1);
    					rate = 1.0f-(float)(viewcenter - getCenterOfGallery() )/(viewcenter - getCenterOfView(tempview));
    				//}
    				if(rate < 0 ) rate = 0;
    				if(rate > 1.0f) rate = 1;
    				view.setScaleX(1+(SELECTED_SCALE-1)*rate);
    				view.setScaleY(1+(SELECTED_SCALE-1)*rate);
    				view.setAlpha(mUnselectedAlpha+(1-mUnselectedAlpha)*rate);
    				Log.d(TAG, "right view.Scale "+i+" "+view.getScaleX());
    			}else{
    				view.setScaleX(SELECTED_SCALE);
    				view.setScaleY(SELECTED_SCALE);
    				view.setAlpha(1);
    			}
    		}
    	}
    	Log.d(TAG,"updateStateOfSelectedView mSelectedPosition... ");
    }
}

