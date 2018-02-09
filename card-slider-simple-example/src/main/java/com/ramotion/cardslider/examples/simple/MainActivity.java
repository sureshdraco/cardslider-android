package com.ramotion.cardslider.examples.simple;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.ramotion.cardslider.CardSliderLayoutManager;
import com.ramotion.cardslider.CardSnapHelper;
import com.ramotion.cardslider.examples.simple.cards.SliderAdapter;

public class MainActivity extends AppCompatActivity {
	
	private final int[] pics = {R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4, R.drawable.p5};
	
	private final SliderAdapter sliderAdapter = new SliderAdapter(pics, 20, new OnCardClickListener());
	
	private CardSliderLayoutManager layoutManger;
	private RecyclerView recyclerView;
	private int currentPosition;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		initRecyclerView();
	}
	
	private void initRecyclerView() {
		recyclerView = findViewById(R.id.recycler_view);
		recyclerView.setAdapter(sliderAdapter);
		recyclerView.setHasFixedSize(true);
		
		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					onActiveCardChange();
				}
			}
		});
		
		layoutManger = (CardSliderLayoutManager) recyclerView.getLayoutManager();
		
		new CardSnapHelper().attachToRecyclerView(recyclerView);
	}
	
	private void onActiveCardChange() {
		final int pos = layoutManger.getActiveCardPosition();
		if (pos == RecyclerView.NO_POSITION || pos == currentPosition) {
			return;
		}
		
		onActiveCardChange(pos);
	}
	
	private void onActiveCardChange(int pos) {
		int animH[] = new int[]{R.anim.slide_in_right, R.anim.slide_out_left};
		int animV[] = new int[]{R.anim.slide_in_top, R.anim.slide_out_bottom};
		
		final boolean left2right = pos < currentPosition;
		if (left2right) {
			animH[0] = R.anim.slide_in_left;
			animH[1] = R.anim.slide_out_right;
			
			animV[0] = R.anim.slide_in_bottom;
			animV[1] = R.anim.slide_out_top;
		}
		currentPosition = pos;
	}
	
	private class OnCardClickListener implements View.OnClickListener {
		@Override
		public void onClick(View view) {
			final CardSliderLayoutManager lm = (CardSliderLayoutManager) recyclerView.getLayoutManager();
			
			if (lm.isSmoothScrolling()) {
				return;
			}
			
			final int activeCardPosition = lm.getActiveCardPosition();
			if (activeCardPosition == RecyclerView.NO_POSITION) {
				return;
			}
			
			final int clickedPosition = recyclerView.getChildAdapterPosition(view);
			if (clickedPosition == activeCardPosition) {
				final Intent intent = new Intent(MainActivity.this, DetailsActivity.class);
				intent.putExtra(DetailsActivity.BUNDLE_IMAGE_ID, pics[activeCardPosition % pics.length]);
				
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
					startActivity(intent);
				} else {
					final CardView cardView = (CardView) view;
					final View sharedView = cardView.getChildAt(cardView.getChildCount() - 1);
					final ActivityOptions options = ActivityOptions
						.makeSceneTransitionAnimation(MainActivity.this, sharedView, "shared");
					startActivity(intent, options.toBundle());
				}
			} else if (clickedPosition > activeCardPosition) {
				recyclerView.smoothScrollToPosition(clickedPosition);
				onActiveCardChange(clickedPosition);
			}
		}
	}
	
}
