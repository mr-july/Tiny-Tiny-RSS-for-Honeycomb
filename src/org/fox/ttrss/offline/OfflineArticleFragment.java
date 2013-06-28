package org.fox.ttrss.offline;

import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.fox.ttrss.R;
import org.fox.ttrss.util.ImageCacheService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.fox.ttrss.util.DatabaseHelper;

public class OfflineArticleFragment extends Fragment implements GestureDetector.OnDoubleTapListener {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private int m_articleId;
	private boolean m_isCat = false; // FIXME use
	private Cursor m_cursor;
	private OfflineActivity m_activity;
	private GestureDetector m_detector;

	public void initialize(int articleId) {
		m_articleId = articleId;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		/* AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo(); */

		switch (item.getItemId()) {
		case R.id.article_link_share:
			m_activity.shareArticle(m_articleId);
			return true;
		case R.id.article_link_copy:
			if (true) {
				Cursor article = m_activity.getArticleById(m_articleId);

				if (article != null) {
					m_activity.copyToClipboard(article.getString(article.getColumnIndex("link")));
					article.close();
				}
			}
			return true;
		default:
			Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {

		//getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
		//menu.setHeaderTitle(m_cursor.getString(m_cursor.getColumnIndex("title")));

		String title = m_cursor.getString(m_cursor.getColumnIndex("title"));

		if (v.getId() == R.id.content) {
			HitTestResult result = ((WebView)v).getHitTestResult();

			if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
				menu.setHeaderTitle(result.getExtra());
				getActivity().getMenuInflater().inflate(R.menu.article_content_img_context_menu, menu);

				/* FIXME I have no idea how to do this correctly ;( */

				m_activity.setLastContentImageHitTestUrl(result.getExtra());

			} else {
				menu.setHeaderTitle(title);
				getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
			}
		} else {
			menu.setHeaderTitle(title);
			getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
		}

		super.onCreateContextMenu(menu, v, menuInfo);

	}

	@SuppressLint("NewApi")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		if (savedInstanceState != null) {
			m_articleId = savedInstanceState.getInt("articleId");
		}

		View view = inflater.inflate(R.layout.article_fragment, container, false);

		m_cursor = m_activity.getReadableDb().query(
      "articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")",
				new String[] {
          "articles.*", "feeds.title AS feed_title",
          "feeds.lang AS feed_lang" },
        "articles." + BaseColumns._ID + "=?",
				new String[] { String.valueOf(m_articleId) }, null, null, null);

		m_cursor.moveToFirst();

		if (m_cursor.isFirst()) {

			TextView title = (TextView)view.findViewById(R.id.title);

			final String link = m_cursor.getString(m_cursor.getColumnIndex("link"));

			if (title != null) {

				String titleStr;

				if (m_cursor.getString(m_cursor.getColumnIndex("title")).length() > 200)
					titleStr = m_cursor.getString(m_cursor.getColumnIndex("title")).substring(0, 200) + "...";
				else
					titleStr = m_cursor.getString(m_cursor.getColumnIndex("title"));

				title.setText(titleStr);
				//title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
				title.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						try {
							URL url = new URL(link.trim());
							String uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
								url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toString();
							Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
							startActivity(intent);
						} catch (Exception e) {
							e.printStackTrace();
							m_activity.toast(R.string.error_other_error);
						}
					}
				});

				registerForContextMenu(title);
			}

			TextView comments = (TextView)view.findViewById(R.id.comments);

			if (comments != null) {
				comments.setVisibility(View.GONE);
			}

			WebView web = (WebView)view.findViewById(R.id.content);

			if (web != null) {

				registerForContextMenu(web);

				web.setWebChromeClient(new WebChromeClient() {
					@Override
	                public void onProgressChanged(WebView view, int progress) {
	                	m_activity.setProgress(Math.round(((float)progress / 100f) * 10000));
	                	if (progress == 100) {
	                		m_activity.setProgressBarVisibility(false);
	                	}
	                }
				});

				web.setOnTouchListener(new View.OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						return m_detector.onTouchEvent(event);
					}
				});

        StringBuilder content = new StringBuilder ();
				StringBuilder cssOverride = new StringBuilder ();

				WebSettings ws = web.getSettings();
				ws.setSupportZoom(true);
				ws.setBuiltInZoomControls(true);

				if (!m_activity.isCompatMode())
					ws.setDisplayZoomControls(false);

				web.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);

				TypedValue tv = new TypedValue();
			    getActivity().getTheme().resolveAttribute(R.attr.linkColor, tv, true);

			    // prevent flicker in ics
			    if (android.os.Build.VERSION.SDK_INT >= 11) {
			    	web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			    }

				if (m_prefs.getString("theme", "THEME_DARK").startsWith ("THEME_DARK")) {
					cssOverride.append ("body {color : #e0e0e0}");
				}

				web.setBackgroundColor(getResources().getColor(android.R.color.transparent));

				String hexColor = String.format("#%06X", (0xFFFFFF & tv.data));
			  cssOverride.append(" a:link {color: ").append(hexColor).append (
          ";} a:visited { color: ").append(hexColor).append(";}");

			  cssOverride.append(" table { width : 100%; }");

				String articleContent = m_cursor.getString(m_cursor.getColumnIndex("content"));
				Document doc = Jsoup.parse(articleContent);

				if (doc != null) {
					if (m_prefs.getBoolean("offline_image_cache_enabled", false)) {

						Elements images = doc.select("img");

						for (Element img : images) {
							String url = img.attr("src");

							if (ImageCacheService.isUrlCached(m_activity, url)) {
								img.attr("src", "file://" + ImageCacheService.getCacheFileName(m_activity, url));
							}
						}
					}

					// thanks webview for crashing on <video> tag
					Elements videos = doc.select("video");

					for (Element video : videos)
						video.remove();

					articleContent = doc.toString();
				}

				String align = m_prefs.getBoolean("justify_article_text", true) ?
          "text-align: justify;" : "";

  			cssOverride.append("body {").append(align);

        int fontSize = 14;

				switch (Integer.parseInt(m_prefs.getString("font_size", "0"))) {
				case 0:
          fontSize = 14;
					break;
				case 1:
          fontSize = 18;
					break;
				case 2:
          fontSize = 21;
					break;
				}

        cssOverride.append("font-size: ").append (fontSize).append ("px;}");

        AssetManager assetManager = m_activity.getAssets();

        InputStream input;
        try
        {
          input = assetManager.open ("template.html");

          BufferedReader br = new BufferedReader (
            new InputStreamReader (input, "UTF-8"));

          String line = null;

          while ((line = br.readLine ()) != null)
          {
            content.append (line).append ("\n");
          }
        } catch (IOException e)
        {
          e.printStackTrace ();
        }


        replaceMarker (content, "###STYLES###", cssOverride.toString ());

        String feedLang = DatabaseHelper.DEFAULT_LANG;
        try
        {
          feedLang = m_cursor.getString (m_cursor.getColumnIndex ("feed_lang"));
        } catch (Exception e)
        {
          e.printStackTrace ();
        }
        replaceMarker (content, "###LANG###", feedLang);

        replaceMarker (content, "###CONTENT###", articleContent);

				try {
					String baseUrl = "fake://ForJS";

          web.getSettings().setJavaScriptEnabled(true);
					web.loadDataWithBaseURL(baseUrl, content.toString (), "text/html",
            "utf-8", null);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}


			}

			TextView dv = (TextView)view.findViewById(R.id.date);

			if (dv != null) {
				Date d = new Date(m_cursor.getInt(m_cursor.getColumnIndex("updated")) * 1000L);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				dv.setText(df.format(d));
			}

			TextView tagv = (TextView)view.findViewById(R.id.tags);

			if (tagv != null) {
				int feedTitleIndex = m_cursor.getColumnIndex("feed_title");

				if (feedTitleIndex != -1 && m_isCat) {
					tagv.setText(m_cursor.getString(feedTitleIndex));
				} else {
					String tagsStr = m_cursor.getString(m_cursor.getColumnIndex("tags"));
					tagv.setText(tagsStr);
				}
			}

			TextView author = (TextView)view.findViewById(R.id.author);

			if (author != null) {
				int authorIndex = m_cursor.getColumnIndex("author");
				if (authorIndex >= 0)
					author.setText(m_cursor.getString(authorIndex));
				else
					author.setVisibility(View.GONE);
			}
		}

		return view;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		m_cursor.close();
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);

		out.putInt("articleId", m_articleId);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

		m_activity = (OfflineActivity) activity;

		m_detector = new GestureDetector(m_activity, new GestureDetector.OnGestureListener() {
			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void onShowPress(MotionEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
					float distanceY) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void onLongPress(MotionEvent e) {
				m_activity.openContextMenu(getView());
			}

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
					float velocityY) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean onDown(MotionEvent e) {
				// TODO Auto-generated method stub
				return false;
			}
		});

		m_detector.setOnDoubleTapListener(this);
	}

	@Override
	public boolean onDoubleTap(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onDoubleTapEvent(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		// TODO Auto-generated method stub
		return false;
	}


  /**
   * replace all occurrences of given marker in string builder with given
   * string
   *
   * @param content       content to be operated
   * @param marker        marker to be replaced
   * @param replacement   new value for marker
   */
  private void replaceMarker (StringBuilder content, String marker,
    String replacement)
  {
    int start = content.indexOf (marker, 0);

    while (start >= 0)
    {
      content.replace (start, start + marker.length (), replacement);
      start = content.indexOf (marker, start + replacement.length ());
    }
  }
}
