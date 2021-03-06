package com.ramotion.cardslider;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;

/**
 * A {@link android.support.v7.widget.RecyclerView.LayoutManager} implementation.
 */
public class CardSliderLayoutManager extends RecyclerView.LayoutManager
	implements RecyclerView.SmoothScroller.ScrollVectorProvider {
	
	private static final int DEFAULT_ACTIVE_CARD_TOP_OFFSET = 50;
	private static final int DEFAULT_CARD_HEIGHT = 148;
	private static final int DEFAULT_CARDS_GAP = 12;
	private static final int TOP_CARD_COUNT = 2;
	
	private final SparseArray<View> viewCache = new SparseArray<>();
	private final SparseIntArray cardsYCoords = new SparseIntArray();
	
	private int cardHeight;
	private int activeCardTop;
	private int activeCardBottom;
	private int activeCardCenter;
	
	private float cardsGap;
	
	private int scrollRequestedPosition = 0;
	
	private ViewUpdater viewUpdater;
	private RecyclerView recyclerView;
	
	/**
	 * A ViewUpdater is invoked whenever a visible/attached card is scrolled.
	 */
	public interface ViewUpdater {
		/**
		 * Called when CardSliderLayoutManager initialized
		 */
		void onLayoutManagerInitialized(@NonNull CardSliderLayoutManager lm);
		
		/**
		 * Called on view update (scroll, layout).
		 *
		 * @param view     Updating view
		 * @param position Position of card relative to the current active card position of the layout manager.
		 *                 0 is active card. 1 is first right card, and -1 is first left (stacked) card.
		 */
		void updateView(@NonNull View view, float position);
	}
	
	private static class SavedState implements Parcelable {
		
		int anchorPos;
		
		SavedState() {
		
		}
		
		SavedState(Parcel in) {
			anchorPos = in.readInt();
		}
		
		public SavedState(SavedState other) {
			anchorPos = other.anchorPos;
		}
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		@Override
		public void writeToParcel(Parcel parcel, int i) {
			parcel.writeInt(anchorPos);
		}
		
		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel parcel) {
				return new SavedState(parcel);
			}
			
			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
		
	}
	
	/**
	 * Creates CardSliderLayoutManager with default values
	 *
	 * @param context Current context, will be used to access resources.
	 */
	public CardSliderLayoutManager(@NonNull Context context) {
		this(context, null, 0, 0);
	}
	
	/**
	 * Constructor used when layout manager is set in XML by RecyclerView attribute
	 * "layoutManager".
	 * <p>
	 * See {@link R.styleable#CardSlider_activeCardLeftOffset}
	 * See {@link R.styleable#CardSlider_cardHeight}
	 * See {@link R.styleable#CardSlider_cardsGap}
	 */
	public CardSliderLayoutManager(@NonNull Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		final float density = context.getResources().getDisplayMetrics().density;
		
		final int defaultCardWidth = (int) (DEFAULT_CARD_HEIGHT * density);
		final int defaultActiveCardLeft = (int) (DEFAULT_ACTIVE_CARD_TOP_OFFSET * density);
		final float defaultCardsGap = DEFAULT_CARDS_GAP * density;
		
		if (attrs == null) {
			initialize(defaultActiveCardLeft, defaultCardWidth, defaultCardsGap, null);
		} else {
			int attrCardWidth;
			int attrActiveCardLeft;
			float attrCardsGap;
			String viewUpdateClassName;
			
			final TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CardSlider, 0, 0);
			try {
				attrCardWidth = a.getDimensionPixelSize(R.styleable.CardSlider_cardHeight, defaultCardWidth);
				attrActiveCardLeft = a.getDimensionPixelSize(R.styleable.CardSlider_activeCardLeftOffset, defaultActiveCardLeft);
				attrCardsGap = a.getDimension(R.styleable.CardSlider_cardsGap, defaultCardsGap);
				viewUpdateClassName = a.getString(R.styleable.CardSlider_viewUpdater);
			} finally {
				a.recycle();
			}
			
			final ViewUpdater viewUpdater = loadViewUpdater(context, viewUpdateClassName, attrs);
			initialize(attrActiveCardLeft, attrCardWidth, attrCardsGap, viewUpdater);
		}
	}
	
	/**
	 * Creates CardSliderLayoutManager with specified values in pixels.
	 *
	 * @param activeCardTop Active card offset from start of RecyclerView. Default value is 50dp.
	 * @param cardHeight    Card width. Default value is 148dp.
	 * @param cardsGap      Distance between cards. Default value is 12dp.
	 */
	public CardSliderLayoutManager(int activeCardTop, int cardHeight, float cardsGap) {
		initialize(activeCardTop, cardHeight, cardsGap, null);
	}
	
	private void initialize(int top, int height, float gap, @Nullable ViewUpdater updater) {
		this.cardHeight = height;
		this.activeCardTop = top;
		this.activeCardBottom = activeCardTop + cardHeight;
		this.activeCardCenter = activeCardTop + ((this.activeCardBottom - activeCardTop) / 2);
		this.cardsGap = gap;
		
		this.viewUpdater = updater;
		if (this.viewUpdater == null) {
			this.viewUpdater = new DefaultViewUpdater();
		}
		viewUpdater.onLayoutManagerInitialized(this);
	}
	
	@Override
	public RecyclerView.LayoutParams generateDefaultLayoutParams() {
		return new RecyclerView.LayoutParams(
			RecyclerView.LayoutParams.WRAP_CONTENT,
			RecyclerView.LayoutParams.WRAP_CONTENT);
	}
	
	@Override
	public void onLayoutChildren(RecyclerView.Recycler recycler, final RecyclerView.State state) {
		if (getItemCount() == 0) {
			removeAndRecycleAllViews(recycler);
			return;
		}
		
		if (getChildCount() == 0 && state.isPreLayout()) {
			return;
		}
		
		int anchorPos = getActiveCardPosition();
		
		if (state.isPreLayout()) {
			final LinkedList<Integer> removed = new LinkedList<>();
			for (int i = 0, cnt = getChildCount(); i < cnt; i++) {
				final View child = getChildAt(i);
				final boolean isRemoved = ((RecyclerView.LayoutParams) child.getLayoutParams()).isItemRemoved();
				if (isRemoved) {
					removed.add(getPosition(child));
				}
			}
			
			if (removed.contains(anchorPos)) {
				final int first = removed.getFirst();
				final int last = removed.getLast();
				
				final int left = first - 1;
				final int right = last == getItemCount() + removed.size() - 1 ? RecyclerView.NO_POSITION : last;
				
				anchorPos = Math.max(left, right);
			}
			
			scrollRequestedPosition = anchorPos;
		}
		
		detachAndScrapAttachedViews(recycler);
		fill(anchorPos, recycler, state);
		
		if (cardsYCoords.size() != 0) {
			layoutByCoords();
		}
		
		if (state.isPreLayout()) {
			recyclerView.postOnAnimationDelayed(new Runnable() {
				@Override
				public void run() {
					updateViewScale();
				}
			}, 415);
		} else {
			updateViewScale();
		}
	}
	
	@Override
	public boolean supportsPredictiveItemAnimations() {
		return true;
	}
	
	@Override
	public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
		removeAllViews();
	}
	
	@Override
	public void scrollToPosition(int position) {
		if (position < 0 || position >= getItemCount()) {
			return;
		}
		
		scrollRequestedPosition = position;
		requestLayout();
	}
	
	@Override
	public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
		scrollRequestedPosition = RecyclerView.NO_POSITION;
		
		int delta;
		if (dy < 0) {
			delta = scrollBottom(Math.max(dy, -cardHeight));
		} else {
			delta = scrollTop(dy);
		}
		
		fill(getActiveCardPosition(), recycler, state);
		updateViewScale();
		
		cardsYCoords.clear();
		for (int i = 0, cnt = getChildCount(); i < cnt; i++) {
			final View view = getChildAt(i);
			cardsYCoords.put(getPosition(view), getDecoratedTop(view));
		}
		
		return delta;
	}
	
	@Override
	public boolean canScrollVertically() {
		return getChildCount() != 0;
	}
	
	@Override
	public PointF computeScrollVectorForPosition(int targetPosition) {
		return new PointF(0, targetPosition - getActiveCardPosition());
	}
	
	@Override
	public void smoothScrollToPosition(final RecyclerView recyclerView, RecyclerView.State state, final int position) {
		if (position < 0 || position >= getItemCount()) {
			return;
		}
		
		final LinearSmoothScroller scroller = getSmoothScroller(recyclerView);
		scroller.setTargetPosition(position);
		startSmoothScroll(scroller);
	}
	
	@Override
	public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int count) {
		final int anchorPos = getActiveCardPosition();
		if (positionStart + count <= anchorPos) {
			scrollRequestedPosition = anchorPos - 1;
		}
	}
	
	@Override
	public Parcelable onSaveInstanceState() {
		SavedState state = new SavedState();
		state.anchorPos = getActiveCardPosition();
		return state;
	}
	
	@Override
	public void onRestoreInstanceState(Parcelable parcelable) {
		if (parcelable instanceof SavedState) {
			SavedState state = (SavedState) parcelable;
			scrollRequestedPosition = state.anchorPos;
			requestLayout();
		}
	}
	
	@Override
	public void onAttachedToWindow(RecyclerView view) {
		super.onAttachedToWindow(view);
		recyclerView = view;
	}
	
	@Override
	public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
		super.onDetachedFromWindow(view, recycler);
		recyclerView = null;
	}
	
	/**
	 * @return active card position or RecyclerView.NO_POSITION
	 */
	public int getActiveCardPosition() {
		if (scrollRequestedPosition != RecyclerView.NO_POSITION) {
			return scrollRequestedPosition;
		} else {
			int result = RecyclerView.NO_POSITION;
			
			View biggestView = null;
			float lastScaleY = 0f;
			
			for (int i = 0, cnt = getChildCount(); i < cnt; i++) {
				final View child = getChildAt(i);
				final int viewTop = getDecoratedTop(child);
				if (viewTop >= activeCardBottom) {
					continue;
				}
				
				final float scaleY = ViewCompat.getScaleY(child);
				if (lastScaleY < scaleY && viewTop < activeCardCenter) {
					lastScaleY = scaleY;
					biggestView = child;
				}
			}
			
			if (biggestView != null) {
				result = getPosition(biggestView);
			}
			
			return result;
		}
	}
	
	@Nullable
	public View getTopView() {
		if (getChildCount() == 0) {
			return null;
		}
		
		View result = null;
		float lastValue = cardHeight;
		
		for (int i = 0, cnt = getChildCount(); i < cnt; i++) {
			final View child = getChildAt(i);
			if (getDecoratedTop(child) >= activeCardBottom) {
				continue;
			}
			
			final int viewTop = getDecoratedTop(child);
			final int diff = activeCardBottom - viewTop;
			if (diff < lastValue) {
				lastValue = diff;
				result = child;
			}
		}
		
		return result;
	}
	
	public int getActiveCardTop() {
		return activeCardTop;
	}
	
	public int getActiveCardBottom() {
		return activeCardBottom;
	}
	
	public int getActiveCardCenter() {
		return activeCardCenter;
	}
	
	public int getCardHeight() {
		return cardHeight;
	}
	
	public float getCardsGap() {
		return cardsGap;
	}
	
	public LinearSmoothScroller getSmoothScroller(final RecyclerView recyclerView) {
		return new LinearSmoothScroller(recyclerView.getContext()) {
			@Override
			public int calculateDyToMakeVisible(View view, int snapPreference) {
				final int viewStart = getDecoratedTop(view);
				if (viewStart > activeCardTop) {
					return activeCardTop - viewStart;
				} else {
					int delta = 0;
					int topViewPos = 0;
					
					final View topView = getTopView();
					if (topView != null) {
						topViewPos = getPosition(topView);
						if (topViewPos != getTargetPosition()) {
							final int topViewLeft = getDecoratedTop(topView);
							if (topViewLeft >= activeCardTop && topViewLeft < activeCardBottom) {
								delta = activeCardBottom - topViewLeft;
							}
						}
					}
					
					return delta + (cardHeight) * Math.max(0, topViewPos - getTargetPosition() - 1);
				}
			}
			
			@Override
			protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
				return 0.5f;
			}
			
		};
	}
	
	private ViewUpdater loadViewUpdater(Context context, String className, AttributeSet attrs) {
		if (className == null || className.trim().length() == 0) {
			return null;
		}
		
		final String fullClassName;
		if (className.charAt(0) == '.') {
			fullClassName = context.getPackageName() + className;
		} else if (className.contains(".")) {
			fullClassName = className;
		} else {
			fullClassName = CardSliderLayoutManager.class.getPackage().getName() + '.' + className;
		}
		
		ViewUpdater updater;
		try {
			final ClassLoader classLoader = context.getClassLoader();
			
			final Class<? extends ViewUpdater> viewUpdaterClass =
				classLoader.loadClass(fullClassName).asSubclass(ViewUpdater.class);
			final Constructor<? extends ViewUpdater> constructor =
				viewUpdaterClass.getConstructor();
			
			constructor.setAccessible(true);
			updater = constructor.newInstance();
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException(attrs.getPositionDescription() +
				": Error creating LayoutManager " + className, e);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(attrs.getPositionDescription()
				+ ": Unable to find ViewUpdater" + className, e);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException(attrs.getPositionDescription()
				+ ": Could not instantiate the ViewUpdater: " + className, e);
		} catch (InstantiationException e) {
			throw new IllegalStateException(attrs.getPositionDescription()
				+ ": Could not instantiate the ViewUpdater: " + className, e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(attrs.getPositionDescription()
				+ ": Cannot access non-public constructor " + className, e);
		} catch (ClassCastException e) {
			throw new IllegalStateException(attrs.getPositionDescription()
				+ ": Class is not a ViewUpdater " + className, e);
		}
		
		return updater;
	}
	
	private int scrollBottom(int dx) {
		final int childCount = getChildCount();
		
		if (childCount == 0) {
			return 0;
		}
		
		final View rightestView = getChildAt(childCount - 1);
		final int deltaBorder = activeCardTop + getPosition(rightestView) * cardHeight;
		final int delta = getAllowedBottomDelta(rightestView, dx, deltaBorder);
		
		final LinkedList<View> rightViews = new LinkedList<>();
		final LinkedList<View> leftViews = new LinkedList<>();
		
		for (int i = childCount - 1; i >= 0; i--) {
			final View view = getChildAt(i);
			final int viewLeft = getDecoratedTop(view);
			
			if (viewLeft >= activeCardBottom) {
				rightViews.add(view);
			} else {
				leftViews.add(view);
			}
		}
		
		for (View view : rightViews) {
			final int border = activeCardTop + getPosition(view) * cardHeight;
			final int allowedDelta = getAllowedBottomDelta(view, dx, border);
			view.offsetTopAndBottom(-allowedDelta);
		}
		
		final int step = activeCardTop / TOP_CARD_COUNT;
		final int jDelta = (int) Math.floor(1f * delta * step / cardHeight);
		
		View prevView = null;
		int j = 0;
		
		for (int i = 0, cnt = leftViews.size(); i < cnt; i++) {
			final View view = leftViews.get(i);
			if (prevView == null || getDecoratedTop(prevView) >= activeCardBottom) {
				final int border = activeCardTop + getPosition(view) * cardHeight;
				final int allowedDelta = getAllowedBottomDelta(view, dx, border);
				view.offsetTopAndBottom(-allowedDelta);
			} else {
				final int border = activeCardTop - step * j;
				view.offsetTopAndBottom(-getAllowedBottomDelta(view, jDelta, border));
				j++;
			}
			
			prevView = view;
		}
		
		return delta;
	}
	
	private int scrollTop(int dy) {
		final int childCount = getChildCount();
		if (childCount == 0) {
			return 0;
		}
		
		final View lastView = getChildAt(childCount - 1);
		final boolean isLastItem = getPosition(lastView) == getItemCount() - 1;
		
		final int delta;
		if (isLastItem) {
			delta = Math.min(dy, getDecoratedBottom(lastView) - activeCardBottom);
		} else {
			delta = dy;
		}
		
		final int step = activeCardTop / TOP_CARD_COUNT;
		final int jDelta = (int) Math.ceil(1f * delta * step / cardHeight);
		
		for (int i = childCount - 1; i >= 0; i--) {
			final View view = getChildAt(i);
			final int viewTop = getDecoratedTop(view);
			
			if (viewTop > activeCardTop) {
				view.offsetTopAndBottom(getAllowedTopDelta(view, delta, activeCardTop));
			} else {
				int border = activeCardTop - step;
				for (int j = i; j >= 0; j--) {
					final View jView = getChildAt(j);
					jView.offsetTopAndBottom(getAllowedTopDelta(jView, jDelta, border));
					border -= step;
				}
				
				break;
			}
		}
		
		return delta;
	}
	
	private int getAllowedTopDelta(@NonNull View view, int dy, int border) {
		final int viewTop = getDecoratedTop(view);
		if (viewTop - dy > border) {
			return -dy;
		} else {
			return border - viewTop;
		}
	}
	
	private int getAllowedBottomDelta(@NonNull View view, int dy, int border) {
		final int viewTop = getDecoratedTop(view);
		if (viewTop + Math.abs(dy) < border) {
			return dy;
		} else {
			return viewTop - border;
		}
	}
	
	private void layoutByCoords() {
		final int count = Math.min(getChildCount(), cardsYCoords.size());
		for (int i = 0; i < count; i++) {
			final View view = getChildAt(i);
			final int viewTop = cardsYCoords.get(getPosition(view));
			layoutDecorated(view, 0, viewTop, getDecoratedRight(view), viewTop + cardHeight);
		}
		cardsYCoords.clear();
	}
	
	private void fill(int anchorPos, RecyclerView.Recycler recycler, RecyclerView.State state) {
		viewCache.clear();
		
		for (int i = 0, cnt = getChildCount(); i < cnt; i++) {
			View view = getChildAt(i);
			int pos = getPosition(view);
			viewCache.put(pos, view);
		}
		
		for (int i = 0, cnt = viewCache.size(); i < cnt; i++) {
			detachView(viewCache.valueAt(i));
		}
		
		if (!state.isPreLayout()) {
			fillLeft(anchorPos, recycler);
			fillRight(anchorPos, recycler);
		}
		
		for (int i = 0, cnt = viewCache.size(); i < cnt; i++) {
			recycler.recycleView(viewCache.valueAt(i));
		}
	}
	
	private void fillLeft(int anchorPos, RecyclerView.Recycler recycler) {
		if (anchorPos == RecyclerView.NO_POSITION) {
			return;
		}
		
		final int layoutStep = activeCardTop / TOP_CARD_COUNT;
		int pos = Math.max(0, anchorPos - TOP_CARD_COUNT - 1);
		int viewTop = Math.max(-1, TOP_CARD_COUNT - (anchorPos - pos)) * layoutStep;
		
		while (pos < anchorPos) {
			View view = viewCache.get(pos);
			if (view != null) {
				attachView(view);
				viewCache.remove(pos);
			} else {
				view = recycler.getViewForPosition(pos);
				addView(view);
				measureChildWithMargins(view, 0, 0);
				final int viewWidth = getDecoratedMeasuredWidth(view);
				layoutDecorated(view, 0, viewTop, viewWidth, viewTop + cardHeight);
			}
			
			viewTop += layoutStep;
			pos++;
		}
		
	}
	
	private void fillRight(int anchorPos, RecyclerView.Recycler recycler) {
		if (anchorPos == RecyclerView.NO_POSITION) {
			return;
		}
		
		final int height = getHeight();
		final int itemCount = getItemCount();
		
		int pos = anchorPos;
		int viewTop = activeCardTop;
		boolean fillBottom = true;
		
		while (fillBottom && pos < itemCount) {
			View view = viewCache.get(pos);
			if (view != null) {
				attachView(view);
				viewCache.remove(pos);
			} else {
				view = recycler.getViewForPosition(pos);
				addView(view);
				measureChildWithMargins(view, 0, 0);
				final int viewWidth = getDecoratedMeasuredWidth(view);
				layoutDecorated(view, 0, viewTop, viewWidth, viewTop + cardHeight);
			}
			
			viewTop = getDecoratedBottom(view);
			fillBottom = viewTop < height + cardHeight;
			pos++;
		}
	}
	
	private void updateViewScale() {
		for (int i = 0, cnt = getChildCount(); i < cnt; i++) {
			final View view = getChildAt(i);
			final int viewTop = getDecoratedTop(view);
			
			final float position = ((float) (viewTop - activeCardTop) / cardHeight);
			viewUpdater.updateView(view, position);
		}
	}
	
}