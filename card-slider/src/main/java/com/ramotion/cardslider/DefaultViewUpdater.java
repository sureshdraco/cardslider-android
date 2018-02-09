package com.ramotion.cardslider;

import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.View;

/**
 * Default implementation of {@link CardSliderLayoutManager.ViewUpdater}
 */
public class DefaultViewUpdater implements CardSliderLayoutManager.ViewUpdater {
	
	public static final float SCALE_TOP = 0.65f;
	public static final float SCALE_CENTER = 0.95f;
	public static final float SCALE_BOTTOM = 0.8f;
	public static final float SCALE_CENTER_TO_TOP = SCALE_CENTER - SCALE_TOP;
	public static final float SCALE_CENTER_TO_BOTTOM = SCALE_CENTER - SCALE_BOTTOM;
	
	public static final int Z_CENTER_1 = 12;
	public static final int Z_CENTER_2 = 16;
	public static final int Z_BOTTOM = 8;
	private static final String TAG = DefaultViewUpdater.class.getSimpleName();
	
	private int cardHeight;
	private int activeCardTop;
	private int activeCardBottom;
	private int activeCardCenter;
	private float cardsGap;
	
	private int transitionEnd;
	private int transitionDistance;
	private float transitionRight2Center;
	
	private CardSliderLayoutManager lm;
	
	private View previewView;
	
	@Override
	public void onLayoutManagerInitialized(@NonNull CardSliderLayoutManager lm) {
		this.lm = lm;
		
		this.cardHeight = lm.getCardHeight();
		this.activeCardTop = lm.getActiveCardTop();
		this.activeCardBottom = lm.getActiveCardBottom();
		this.activeCardCenter = lm.getActiveCardCenter();
		this.cardsGap = lm.getCardsGap();
		
		this.transitionEnd = activeCardCenter;
		this.transitionDistance = activeCardBottom - transitionEnd;
		
		final float centerBorder = (cardHeight - cardHeight * SCALE_CENTER) / 2f;
		final float rightBorder = (cardHeight - cardHeight * SCALE_BOTTOM) / 2f;
		final float right2centerDistance = (activeCardBottom + centerBorder) - (activeCardBottom - rightBorder);
		this.transitionRight2Center = right2centerDistance - cardsGap;
	}
	
	@Override
	public void updateView(@NonNull View view, float position) {
		final float scale;
		final float alpha;
		final float z;
		final float y;
		
		if (position < 0) {
			final float ratio = (float) lm.getDecoratedTop(view) / activeCardTop;
			scale = SCALE_TOP + SCALE_CENTER_TO_TOP * ratio;
			alpha = 0.1f + ratio;
			z = Z_CENTER_1 * ratio;
			y = 0;
		} else if (position < 0.5f) {
			scale = SCALE_CENTER;
			alpha = 1;
			z = Z_CENTER_1;
			y = 0;
		} else if (position < 1f) {
			final int viewLeft = lm.getDecoratedTop(view);
			final float ratio = (float) (viewLeft - activeCardCenter) / (activeCardBottom - activeCardCenter);
			scale = SCALE_CENTER - SCALE_CENTER_TO_BOTTOM * ratio;
			alpha = 1;
			z = Z_CENTER_2;
			if (Math.abs(transitionRight2Center) < Math.abs(transitionRight2Center * (viewLeft - transitionEnd) / transitionDistance)) {
				y = -transitionRight2Center;
			} else {
				y = -transitionRight2Center * (viewLeft - transitionEnd) / transitionDistance;
			}
		} else {
			scale = SCALE_BOTTOM;
			alpha = 1;
			z = Z_BOTTOM;
			
			if (previewView != null) {
				final float prevViewScale;
				final float prevTransition;
				final int prevRight;
				
				final boolean isFirstRight = lm.getDecoratedBottom(previewView) <= activeCardBottom;
				if (isFirstRight) {
					prevViewScale = SCALE_CENTER;
					prevRight = activeCardBottom;
					prevTransition = 0;
				} else {
					prevViewScale = ViewCompat.getScaleY(previewView);
					prevRight = lm.getDecoratedBottom(previewView);
					prevTransition = ViewCompat.getTranslationY(previewView);
				}
				
				final float prevBorder = (cardHeight - cardHeight * prevViewScale) / 2;
				final float currentBorder = (cardHeight - cardHeight * SCALE_BOTTOM) / 2;
				final float distance = (lm.getDecoratedTop(view) + currentBorder) - (prevRight - prevBorder + prevTransition);
				
				final float transition = distance - cardsGap;
				y = -transition;
			} else {
				y = 0;
			}
		}
		Log.d(TAG, "y:" + y);
		Log.d(TAG, "z:" + z);
		Log.d(TAG, "scale:" + scale);
		ViewCompat.setScaleX(view, scale);
		ViewCompat.setScaleY(view, scale);
		ViewCompat.setZ(view, z);
		ViewCompat.setTranslationY(view, y);
		ViewCompat.setAlpha(view, alpha);
		
		previewView = view;
	}
	
	protected CardSliderLayoutManager getLayoutManager() {
		return lm;
	}
	
}
