package com.wangwei.horizontalscroll;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;


public class MainActivity extends Activity{
  protected void onCreate(Bundle paramBundle)
  {
    super.onCreate(paramBundle);
    setContentView(R.layout.main);
    // Reference the Gallery view
      CoverFlow g = (CoverFlow) findViewById(R.id.gallery);
    // Set the adapter to our custom adapter (below)
    g.setAdapter(new ImageAdapter(this));
  }
  
  public class ImageAdapter extends BaseAdapter {
      private static final int ITEM_WIDTH = 150;
      private static final int ITEM_HEIGHT = 200;

      //private final int mGalleryItemBackground;
      private final Context mContext;

      private final Integer[] mImageIds = {
              R.drawable.one,
              R.drawable.two,
              R.drawable.three,
              R.drawable.four,
              R.drawable.five,
              R.drawable.six,
              R.drawable.seven,
              R.drawable.eight,
              R.drawable.nine,
      };

      private final float mDensity;

      public ImageAdapter(Context c) {
          mContext = c;
          // See res/values/attrs.xml for the <declare-styleable> that defines
          // Gallery1.
          /*TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
          mGalleryItemBackground = a.getResourceId(
                  R.styleable.Gallery1_android_galleryItemBackground, 0);
          a.recycle();*/

          mDensity = c.getResources().getDisplayMetrics().density;
      }

      public int getCount() {
          return mImageIds.length*20;
      }

      public Object getItem(int position) {
          return position;
      }

      public long getItemId(int position) {
          return position;
      }

      public View getView(int position, View convertView, ViewGroup parent) {
          ImageView imageView;
          if (convertView == null) {
              convertView = new ImageView(mContext);

              imageView = (ImageView) convertView;
              imageView.setScaleType(ImageView.ScaleType.FIT_XY);
              imageView.setLayoutParams(new CoverFlow.LayoutParams(
                      (int) (ITEM_WIDTH * mDensity + 0.5f),
                      (int) (ITEM_HEIGHT * mDensity + 0.5f)));
              imageView.setPadding(20, 20, 20, 20);
          
              // The preferred Gallery item background
              //imageView.setBackgroundResource(mGalleryItemBackground);
          } else {
              imageView = (ImageView) convertView;
          }

          imageView.setImageResource(mImageIds[position%9]);

          return imageView;
      }
  }
}
