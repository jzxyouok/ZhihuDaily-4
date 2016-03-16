package com.cundong.izhihu.fragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.cundong.izhihu.Constants;
import com.cundong.izhihu.R;
import com.cundong.izhihu.ZhihuApplication;
import com.cundong.izhihu.activity.NewsDetailActivity;
import com.cundong.izhihu.adapter.NewsAdapter;
import com.cundong.izhihu.db.NewsDataSource;
import com.cundong.izhihu.entity.NewsDetailEntity;
import com.cundong.izhihu.entity.NewsListEntity;
import com.cundong.izhihu.entity.NewsListEntity.NewsEntity;
import com.cundong.izhihu.task.BaseGetNewsTask;
import com.cundong.izhihu.task.BaseGetNewsTask.ResponseListener;
import com.cundong.izhihu.task.GetLatestNewsTask;
import com.cundong.izhihu.task.MyAsyncTask;
import com.cundong.izhihu.util.GsonUtils;
import com.cundong.izhihu.util.ListUtils;
import com.cundong.izhihu.util.ZhihuUtils;
import com.daimajia.slider.library.SliderLayout;
import com.daimajia.slider.library.Animations.DescriptionAnimation;
import com.daimajia.slider.library.SliderTypes.BaseSliderView.OnSliderClickListener;
import com.daimajia.slider.library.SliderTypes.TextSliderView;
import com.daimajia.slider.library.SliderTypes.BaseSliderView;
import com.daimajia.slider.library.Tricks.ViewPagerEx.OnPageChangeListener;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public class NewsListFragment extends BaseFragment implements ResponseListener, OnItemClickListener, OnSliderClickListener, OnPageChangeListener {

	private ListView mListView;
	private ProgressBar mProgressBar;
	private NewsAdapter mAdapter = null;
	
	private ArrayList<NewsEntity> mNewsList = null;
	
	//上次listView滚动到最下方时，itemId
	private int mListViewPreLast = 0;
	private String mCurrentDate = null;
	private SwipeRefreshLayout mSwipeRefreshLayout;
	private SliderLayout mDemoSlider;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		new LoadCacheNewsTask().executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
		new GetLatestNewsTask(getActivity(), this).executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_main, container, false);
		mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.ptr_layout);
		mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener(){
			@Override
			public void onRefresh() {
				// TODO Auto-generated method stub
				doRefresh();
			}});
		
		mListView = (ListView) view.findViewById(R.id.list);
		mListView.setOnItemClickListener(this);
		mProgressBar = (ProgressBar) view.findViewById(R.id.progress);
		
		View headView = LayoutInflater.from(getActivity()).inflate(R.layout.head_item, null);
		mDemoSlider = (SliderLayout)headView.findViewById(R.id.slider);
		mListView.addHeaderView(headView);
	
	   mDemoSlider.setPresetTransformer(SliderLayout.Transformer.Accordion);
	   mDemoSlider.setPresetIndicator(SliderLayout.PresetIndicators.Center_Bottom);
	   mDemoSlider.setCustomAnimation(new DescriptionAnimation());
	   mDemoSlider.setDuration(3000);
	   mDemoSlider.addOnPageChangeListener(this);
	        
		return view;
	}
	
	@Override
	public void onActivityCreated( Bundle savedInstanceState) {
		
		super.onActivityCreated(savedInstanceState);
		
		mListView.setOnScrollListener(new OnScrollListener() {

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				
				final int lastItem = firstVisibleItem + visibleItemCount;
				
				if (lastItem == totalItemCount) {
					if (mListViewPreLast != lastItem) { // to avoid multiple calls for
						
						mCurrentDate = ZhihuUtils.getBeforeDate(mCurrentDate);
						
						new GetMoreNewsTask(getActivity(), null).executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR, mCurrentDate);
						
						mListViewPreLast = lastItem;
					}
				}
			}
			
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				
			}
		});
	}

	private void setAdapter(ArrayList<NewsEntity> newsList) {
		if (mAdapter == null) {
			mAdapter = new NewsAdapter(getActivity(), newsList);
			mListView.setAdapter(mAdapter);
		} else {
			mAdapter.updateData(newsList);
		}
			     
		mDemoSlider.removeAllSliders();
	    for(int i = 0; i < Math.min(5, newsList.size()); i++){
	    	 TextSliderView textSliderView = new TextSliderView(getActivity());
	    	 NewsEntity newsEntity = newsList.get(i);
	    	 if(!newsEntity.isTag ){
	    		// initialize a SliderLayout
		            textSliderView
		                    .description(newsEntity.title)
		                    .image(newsEntity.images.get(0))
		                    .setScaleType(BaseSliderView.ScaleType.Fit)
		                    .setOnSliderClickListener(this);
		            //add your extra information
		            textSliderView.bundle(new Bundle());
		            textSliderView.getBundle().putInt("extra",i);
		            mDemoSlider.addSlider(textSliderView);
	    	 }
	     }  
	}

	private void setListShown(boolean isListViewShown) {
		mListView.setVisibility(isListViewShown ? View.VISIBLE : View.GONE);
		mProgressBar.setVisibility(isListViewShown ? View.GONE : View.VISIBLE);
	}
	
	//读取缓存中的最新新闻
	private class LoadCacheNewsTask extends MyAsyncTask<String, Void, NewsListEntity> {

		@Override
		protected NewsListEntity doInBackground(String... params) {

			NewsListEntity latestNewsEntity = ZhihuApplication.getDataSource().getLatestNews();
			
			if (latestNewsEntity != null) {
				mCurrentDate = latestNewsEntity.date;
				ZhihuUtils.setReadStatus4NewsList(latestNewsEntity.stories);
			}
			
			return latestNewsEntity;
		}

		@Override
		protected void onPostExecute(NewsListEntity result) {
			super.onPostExecute(result);
			
			if(!isAdded())
				return;
			
			if (result != null && !ListUtils.isEmpty(result.stories)) {
				
				NewsEntity tagNewsEntity = new NewsEntity();
				tagNewsEntity.isTag = true;
				tagNewsEntity.title = result.date;
				
				mNewsList = new ArrayList<NewsEntity>();
				mNewsList.add(tagNewsEntity);
				mNewsList.addAll(result.stories);
				
				setAdapter(mNewsList);
			}
		}
	}
	
	//下载过往的新闻
	private class GetMoreNewsTask extends BaseGetNewsTask {

		public GetMoreNewsTask(Context context, ResponseListener listener) {
			super(context, listener);
		}
		
		@Override
		protected NewsListEntity doInBackground(String... params) {
			
			if (params.length == 0)
				return null;
			
			String theKey = params[0];
			
			String oldContent = ((NewsDataSource) getDataSource()).getContent(theKey);
			
			NewsListEntity newsListEntity = null;
			
			if (!TextUtils.isEmpty(oldContent)) {
				newsListEntity = (NewsListEntity) GsonUtils.getEntity(oldContent, NewsListEntity.class);
				if (newsListEntity != null) {
					ZhihuUtils.setReadStatus4NewsList(newsListEntity.stories);
				}
				return newsListEntity;
			} else {
				
				String newContent = null;
				
				try {
					newContent = getUrl(Constants.Url.URLDEFORE + ZhihuUtils.getAddedDate(theKey));
	
					newsListEntity = (NewsListEntity)GsonUtils.getEntity(newContent, NewsListEntity.class);
					
					isRefreshSuccess = !ListUtils.isEmpty(newsListEntity.stories);
				} catch (IOException e) {
					e.printStackTrace();
					
					this.isRefreshSuccess = false;
					this.mException = e;
				} catch (Exception e) {
					e.printStackTrace();

					this.isRefreshSuccess = false;
					this.mException = e;
				}
				
				isContentSame = checkIsContentSame(oldContent, newContent);
				
				if (isRefreshSuccess && !isContentSame) {
					((NewsDataSource) getDataSource()).insertOrUpdateNewsList(Constants.NEWS_LIST, theKey, newContent);
				}
				
				if (newsListEntity != null) {
					ZhihuUtils.setReadStatus4NewsList(newsListEntity.stories);
				}
				
				return newsListEntity;
			}
		}

		@Override
		protected void onPostExecute(NewsListEntity result) {
			super.onPostExecute(result);

			if(!isAdded())
				return;
			
			setListShown(true);

			mListViewPreLast = 0;
			
			if (mNewsList == null) {
				mNewsList = new ArrayList<NewsEntity>();
			}
			
			if (result != null && !ListUtils.isEmpty(result.stories)) {
				
				NewsEntity tagNewsEntity = new NewsEntity();
				tagNewsEntity.isTag = true;
				tagNewsEntity.title = result.date;
				mNewsList.add(tagNewsEntity);
				mNewsList.addAll(result.stories);
				
				setAdapter(mNewsList);	
			}
		}
	}
	
	
	@Override
	public void onPostExecute(NewsListEntity result) {
		if(!isAdded())
			return;
		
		// Notify PullToRefreshLayout that the refresh has finished
//		mPullToRefreshLayout.setRefreshComplete();
		mSwipeRefreshLayout.setRefreshing(false);
					
		if (getView() != null) {
			// Show the list again
			setListShown(true);
		}
		
		if (result != null) {
			mNewsList = new ArrayList<NewsEntity>();

			NewsEntity tagNewsEntity = new NewsEntity();
			tagNewsEntity.isTag = true;
			tagNewsEntity.title = result.date;
			mNewsList.add(tagNewsEntity);

			mNewsList.addAll(result.stories);

			mCurrentDate = result.date;

			setAdapter(mNewsList);
		}
	}
	
	@Override
	public void onFail(Exception e) {
		
		if (getView() != null) {
			// Show the list again
			setListShown(true);
		}
		
		dealException(e);
	}

	@Override
	protected void doRefresh() {
		
		// Hide the list
		setListShown( mNewsList==null ||mNewsList.isEmpty() ? false : true );
		
		new GetLatestNewsTask(getActivity(), this).executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		
		NewsEntity newsEntity = mNewsList != null ? mNewsList.get(position -1) : null;
		Log.i("NewsListFragment", " position " + position + " data " + mNewsList.get(position).title);
		if (newsEntity == null || newsEntity.isTag)
			return;

		Intent intent = new Intent();
		intent.putExtra("id", newsEntity.id);
		intent.putExtra("newsEntity", newsEntity);
		intent.setClass(getActivity(), NewsDetailActivity.class);
		startActivity(intent);
		
		new SetReadFlagTask(newsEntity).executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	public void updateList() {
		new LoadCacheNewsTask().executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
	}

	@Override
	protected void onRestoreState(Bundle savedInstanceState) {

	}

	@Override
	protected void onSaveState(Bundle outState) {

	}

	@Override
	protected void onFirstTimeLaunched() {

	}
	
	private class SetReadFlagTask extends MyAsyncTask<String, Void, Boolean> {

		private NewsEntity mNewsEntity;

		public SetReadFlagTask(NewsEntity newsEntity) {
			mNewsEntity = newsEntity;
		}

		@Override
		protected Boolean doInBackground(String... params) {
			return ZhihuApplication.getNewsReadDataSource().readNews(String.valueOf(mNewsEntity.id));
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if (result) {
				ZhihuUtils.setReadStatus4NewsEntity(mNewsList, mNewsEntity);
				mAdapter.updateData(mNewsList);
			}
		}
	}


	@Override
	public void onSliderClick(BaseSliderView slider) {
		// TODO Auto-generated method stub
		int clickIndex = slider.getBundle().getInt("extra"); 
		NewsEntity newsEntity = mNewsList.get(clickIndex);
		if (newsEntity == null || newsEntity.isTag)
			return;

		Intent intent = new Intent();
		intent.putExtra("id", newsEntity.id);
		intent.putExtra("newsEntity", newsEntity);
		intent.setClass(getActivity(), NewsDetailActivity.class);
		startActivity(intent);
		
		new SetReadFlagTask(newsEntity).executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
//		Crouton.makeText(getActivity(), "" + clickIndex , Style.INFO).show();
	}

	@Override
	public void onPageScrolled(int position, float positionOffset,
			int positionOffsetPixels) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onPageSelected(int position) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onPageScrollStateChanged(int state) {
		// TODO Auto-generated method stub
		
	}
}