package org.fox.ttrss.offline;

import org.fox.ttrss.ActivityTitle;
import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.PreferencesActivity;
import org.fox.ttrss.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;

import java.io.File;

public class OfflineActivity extends CommonActivity {
	private final String TAG = this.getClass().getSimpleName();

	protected SharedPreferences m_prefs;
	protected Menu m_menu;

	private ActionMode m_headlinesActionMode;
	private HeadlinesActionModeCallback m_headlinesActionModeCallback;

	private String m_lastImageHitTestUrl;

  protected ActivityTitle activityTitle;

	@SuppressLint("NewApi")
	private class HeadlinesActionModeCallback implements ActionMode.Callback {

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			m_headlinesActionMode = null;
			deselectAllArticles();
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {

			 MenuInflater inflater = getSupportMenuInflater();
	            inflater.inflate(R.menu.headlines_action_menu, menu);

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			onOptionsItemSelected(item);
			return false;
		}
	};

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		/* AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo(); */

		final OfflineArticlePager ap = (OfflineArticlePager)getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

		switch (item.getItemId()) {
		case R.id.article_img_open:
			if (getLastContentImageHitTestUrl() != null) {
				try {
					Intent intent = new Intent(Intent.ACTION_VIEW,
							Uri.parse(getLastContentImageHitTestUrl()));
					startActivity(intent);
				} catch (Exception e) {
					e.printStackTrace();
					toast(R.string.error_other_error);
				}
			}
			return true;
		case R.id.article_img_share:
			if (getLastContentImageHitTestUrl() != null) {
				Intent intent = new Intent(Intent.ACTION_SEND);

				intent.setType("image/png");
				intent.putExtra(Intent.EXTRA_SUBJECT, getLastContentImageHitTestUrl());
				intent.putExtra(Intent.EXTRA_TEXT, getLastContentImageHitTestUrl());

				startActivity(Intent.createChooser(intent, getLastContentImageHitTestUrl()));
			}
			return true;
		case R.id.article_img_view_caption:
			if (getLastContentImageHitTestUrl() != null) {

				String content = "";

				Cursor article = getArticleById(ap.getSelectedArticleId());

				if (article != null) {
					content = article.getString(article.getColumnIndex("content"));
					article.close();
				}

                // Android doesn't give us an easy way to access title tags;
                // we'll use Jsoup on the body text to grab the title text
                // from the first image tag with this url. This will show
                // the wrong text if an image is used multiple times.
                Document doc = Jsoup.parse(content);
                Elements es = doc.getElementsByAttributeValue("src", getLastContentImageHitTestUrl());
                if (es.size() > 0){
                    if (es.get(0).hasAttr("title")){
                        Dialog dia = new Dialog(this);
                        if (es.get(0).hasAttr("alt")){
                            dia.setTitle(es.get(0).attr("alt"));
                        } else {
                            dia.setTitle(es.get(0).attr("title"));
                        }
                        TextView titleText = new TextView(this);

                        if (android.os.Build.VERSION.SDK_INT >= 16) {
                        	titleText.setPaddingRelative(24, 24, 24, 24);
                        } else {
                        	titleText.setPadding(24, 24, 24, 24);
                        }

                        titleText.setTextSize(16);
                        titleText.setText(es.get(0).attr("title"));
                        dia.setContentView(titleText);
                        dia.show();
                    } else {
                        toast(R.string.no_caption_to_display);
                    }
                } else {
                    toast(R.string.no_caption_to_display);
                }
            }
            return true;
		default:
			Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		setAppTheme(m_prefs);

		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_PROGRESS);

		setProgressBarVisibility(false);

		setContentView(R.layout.login);

		setLoadingStatus(R.string.blank, false);
		findViewById(R.id.loading_container).setVisibility(View.GONE);

		initMenu();

		Intent intent = getIntent();

		if (intent.getExtras() != null) {
			if (intent.getBooleanExtra("initial", false)) {
				intent = new Intent(OfflineActivity.this, OfflineFeedsActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

				startActivityForResult(intent, 0);
				finish();
			}
		}

		/* if (savedInstanceState != null) {

		} */

		m_headlinesActionModeCallback = new HeadlinesActionModeCallback();

    activityTitle = (savedInstanceState != null &&
      savedInstanceState.containsKey ("activityTitle")) ?
      (ActivityTitle)savedInstanceState.getParcelable ("activityTitle") :
      getActivityTitleForId (0, false);

    if (activityTitle != null)
    {
      updateActivityTitle (activityTitle);
    }
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);

    out.putParcelable ("activityTitle", activityTitle);
	}

	protected void selectArticles(int feedId, boolean isCat, int mode) {
		switch (mode) {
		case 0:
			SQLiteStatement stmtSelectAll = null;

			if (isCat) {
				stmtSelectAll = getWritableDb().compileStatement(
						"UPDATE articles SET selected = 1 WHERE feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");
			} else {
				stmtSelectAll = getWritableDb().compileStatement(
								"UPDATE articles SET selected = 1 WHERE feed_id = ?");
			}

			stmtSelectAll.bindLong(1, feedId);
			stmtSelectAll.execute();
			stmtSelectAll.close();

			break;
		case 1:

			SQLiteStatement stmtSelectUnread = null;

			if (isCat) {
				stmtSelectUnread = getWritableDb().compileStatement(
						"UPDATE articles SET selected = 1 WHERE feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?) AND unread = 1");
			} else {
				stmtSelectUnread = getWritableDb().compileStatement(
								"UPDATE articles SET selected = 1 WHERE feed_id = ? AND unread = 1");
			}

			stmtSelectUnread.bindLong(1, feedId);
			stmtSelectUnread.execute();
			stmtSelectUnread.close();

			break;
		case 2:
			deselectAllArticles();
			break;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		/* final OfflineFeedsFragment off = (OfflineFeedsFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_FEEDS); */

		/* final OfflineFeedCategoriesFragment ocf = (OfflineFeedCategoriesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_CATS); */

		final OfflineArticlePager oap = (OfflineArticlePager) getSupportFragmentManager()
				.findFragmentByTag(FRAG_ARTICLE);

		switch (item.getItemId()) {
		/* case android.R.id.home:
			finish();
			return true; */
		case R.id.go_online:
			switchOnline();
			return true;
		case R.id.search:
			if (ohf != null && isCompatMode()) {
				Dialog dialog = new Dialog(this);

				final EditText edit = new EditText(this);

				AlertDialog.Builder builder = new AlertDialog.Builder(this)
						.setTitle(R.string.search)
						.setPositiveButton(getString(R.string.search),
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {

										String query = edit.getText().toString().trim();

										ohf.setSearchQuery(query);

									}
								})
						.setNegativeButton(getString(R.string.cancel),
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {

										//

									}
								}).setView(edit);

				dialog = builder.create();
				dialog.show();
			}

			return true;
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, 0);
			return true;
		case R.id.headlines_view_mode:
			if (ohf != null) {
				Dialog dialog = new Dialog(this);

				String viewMode = getViewMode();

				//Log.d(TAG, "viewMode:" + getViewMode());

				int selectedIndex = 0;

				if (viewMode.equals("all_articles")) {
					selectedIndex = 0;
				} else if (viewMode.equals("marked")) {
					selectedIndex = 1;
				} else if (viewMode.equals("published")) {
					selectedIndex = 2;
				} else if (viewMode.equals("unread")) {
					selectedIndex = 3;
				}

				AlertDialog.Builder builder = new AlertDialog.Builder(this)
						.setTitle(R.string.headlines_set_view_mode)
						.setSingleChoiceItems(
								new String[] {
										/* getString(R.string.headlines_adaptive), */
										getString(R.string.headlines_all_articles),
										getString(R.string.headlines_starred),
										getString(R.string.headlines_published),
										getString(R.string.headlines_unread) },
								selectedIndex, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										switch (which) {
										/* case 0:
											setViewMode("adaptive");
											break; */
										case 0:
											setViewMode("all_articles");
											break;
										case 1:
											setViewMode("marked");
											break;
										case 2:
											setViewMode("published");
											break;
										case 3:
											setViewMode("unread");
											break;
										}
										dialog.cancel();

										refresh();
									}
								});

				dialog = builder.create();
				dialog.show();

			}
			return true;
		case R.id.headlines_select:
			if (ohf != null) {
				Dialog dialog = new Dialog(this);
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.headlines_select_dialog);

				builder.setSingleChoiceItems(new String[] {
						getString(R.string.headlines_select_all),
						getString(R.string.headlines_select_unread),
						getString(R.string.headlines_select_none) }, 0,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {

								selectArticles(ohf.getFeedId(), ohf.getFeedIsCat(), which);
								initMenu();
								refresh();

								dialog.cancel();
							}
						});

				dialog = builder.create();
				dialog.show();
			}
			return true;
		case R.id.headlines_mark_as_read:
			if (ohf != null) {
				final int feedId = ohf.getFeedId();
				final boolean isCat = ohf.getFeedIsCat();

				int count = getUnreadArticleCount(feedId, isCat);
				boolean confirm = m_prefs.getBoolean("confirm_headlines_catchup", true);

				if (count > 0) {
					if (confirm) {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								OfflineActivity.this)
								.setMessage(getString(R.string.mark_num_headlines_as_read, count))
								.setPositiveButton(R.string.catchup,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
													int which) {

												catchupFeed(feedId, isCat);
                        onBackPressed ();

											}
										})
								.setNegativeButton(R.string.dialog_cancel,
										new Dialog.OnClickListener() {
											public void onClick(DialogInterface dialog,
													int which) {

											}
										});

						AlertDialog dlg = builder.create();
						dlg.show();


					} else {
						catchupFeed(feedId, isCat);
					}
				}
			}
			return true;
		case R.id.share_article:
			if (true) {
				int articleId = oap.getSelectedArticleId();

				shareArticle(articleId);
			}
			return true;
		case R.id.toggle_marked:
			if (oap != null) {
				int articleId = oap.getSelectedArticleId();

				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET modified = 1, marked = NOT marked WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();

				refresh();
			}
			return true;
		/* case R.id.selection_select_none:
			deselectAllArticles();
			return true; */
		case R.id.selection_toggle_unread:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET modified = 1, unread = NOT unread WHERE selected = 1");
				stmt.execute();
				stmt.close();

				refresh();
			}
			return true;
		case R.id.selection_toggle_marked:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET modified = 1, marked = NOT marked WHERE selected = 1");
				stmt.execute();
				stmt.close();

				refresh();
			}
			return true;
		case R.id.selection_toggle_published:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = getWritableDb()
						.compileStatement(
								"UPDATE articles SET modified = 1, published = NOT published WHERE selected = 1");
				stmt.execute();
				stmt.close();

				refresh();
			}
			return true;
		case R.id.toggle_published:
			if (oap != null) {
				int articleId = oap.getSelectedArticleId();

				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET modified = 1, published = NOT published WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();

				refresh();
			}
			return true;
		case R.id.catchup_above:
			if (oap != null) {
				int articleId = oap.getSelectedArticleId();
				int feedId = oap.getFeedId();
				boolean isCat = oap.getFeedIsCat();

				SQLiteStatement stmt = null;

				if (isCat) {
					stmt = getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE " +
							"updated >= (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
							"AND feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");
				} else {
					stmt = getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE " +
							"updated >= (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
							"AND feed_id = ?");
				}

				stmt.bindLong(1, articleId);
				stmt.bindLong(2, feedId);
				stmt.execute();
				stmt.close();

				refresh();
			}
			return true;
		case R.id.set_unread:
			if (oap != null) {
				int articleId = oap.getSelectedArticleId();

				SQLiteStatement stmt = getWritableDb().compileStatement(
						"UPDATE articles SET modified = 1, unread = 1 WHERE "
								+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();

				refresh();
			}
			return true;
		default:
			Log.d(TAG, "onOptionsItemSelected, unhandled id=" + item.getItemId());
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.offline_menu, menu);

		m_menu = menu;

		initMenu();

		return true;
	}

	@SuppressLint("NewApi")
	protected void initMenu() {
		if (m_menu != null) {
			m_menu.setGroupVisible(R.id.menu_group_headlines, false);
			m_menu.setGroupVisible(R.id.menu_group_article, false);
			m_menu.setGroupVisible(R.id.menu_group_feeds, false);

			OfflineHeadlinesFragment hf = (OfflineHeadlinesFragment) getSupportFragmentManager().findFragmentByTag(FRAG_HEADLINES);

			if (hf != null) {
				if (hf.getSelectedArticleCount() > 0 && m_headlinesActionMode == null) {
					m_headlinesActionMode = startActionMode(m_headlinesActionModeCallback);
				} else if (hf.getSelectedArticleCount() == 0 && m_headlinesActionMode != null) {
					m_headlinesActionMode.finish();
				}
			}

			OfflineArticlePager ap = (OfflineArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

			if (ap != null) {
				int articleId = ap.getSelectedArticleId();

				Cursor article = getArticleById(articleId);

				if (article != null) {
					boolean unread = article.getInt(article.getColumnIndex("unread")) == 1;
					boolean marked = article.getInt(article.getColumnIndex("marked")) == 1;
					boolean published = article.getInt(article.getColumnIndex("published")) == 1;

					m_menu.findItem(R.id.toggle_marked).setIcon(marked ? R.drawable.ic_important_light :
						R.drawable.ic_unimportant_light);

					m_menu.findItem(R.id.toggle_published).setIcon(published ? R.drawable.ic_menu_published_light :
						R.drawable.ic_menu_unpublished_light);

					m_menu.findItem(R.id.set_unread).setIcon(unread ? R.drawable.ic_unread_light :
						R.drawable.ic_read_light);

					article.close();
				}
			}

			if (!isCompatMode()) {
				MenuItem search = m_menu.findItem(R.id.search);

				SearchView searchView = (SearchView) search.getActionView();
				searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
					private String query = "";

					@Override
					public boolean onQueryTextSubmit(String query) {
						OfflineHeadlinesFragment frag = (OfflineHeadlinesFragment) getSupportFragmentManager()
								.findFragmentByTag(FRAG_HEADLINES);

						if (frag != null) {
							frag.setSearchQuery(query);
							this.query = query;
						}

						return false;
					}

					@Override
					public boolean onQueryTextChange(String newText) {
						if (newText.equals("") && !newText.equals(this.query)) {
							OfflineHeadlinesFragment frag = (OfflineHeadlinesFragment) getSupportFragmentManager()
									.findFragmentByTag(FRAG_HEADLINES);

							if (frag != null) {
								frag.setSearchQuery(newText);
								this.query = newText;
							}
						}

						return false;
					}
				});
			}
		}
	}

	private void switchOnline() {
		SharedPreferences localPrefs = getSharedPreferences("localprefs", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = localPrefs.edit();
		editor.putBoolean("offline_mode_active", false);
		editor.commit();

		Intent refresh = new Intent(this, org.fox.ttrss.OnlineActivity.class);
		startActivity(refresh);
		finish();
	}

	protected Cursor getArticleById(int articleId) {
		Cursor c = getReadableDb().query("articles", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(articleId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (m_prefs.getBoolean("use_volume_keys", false)) {
			OfflineArticlePager ap = (OfflineArticlePager) getSupportFragmentManager().findFragmentByTag(FRAG_ARTICLE);

			if (ap != null && ap.isAdded()) {
				switch (keyCode) {
				case KeyEvent.KEYCODE_VOLUME_UP:
					ap.selectArticle(false);
					return true;
				case KeyEvent.KEYCODE_VOLUME_DOWN:
					ap.selectArticle(true);
					return true;
				}
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	// Handle onKeyUp too to suppress beep
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (m_prefs.getBoolean("use_volume_keys", false)) {

			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				return true;
			}
		}

		return super.onKeyUp(keyCode, event);
	}

	protected Cursor getFeedById(int feedId) {
		Cursor c = getReadableDb().query("feeds", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(feedId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

	protected Cursor getCatById(int catId) {
		Cursor c = getReadableDb().query("categories", null,
				BaseColumns._ID + "=?",
				new String[] { String.valueOf(catId) }, null, null, null);

		c.moveToFirst();

		return c;
	}

  protected ActivityTitle getActivityTitleForId (int id, boolean isCat) {
    CharSequence title = null;
    Drawable icon = null;

    if (id > 0)
    {
      Cursor c = isCat ? getCatById (id) : getFeedById (id);

      if (c != null)
      {
        title = c.getString (c.getColumnIndex ("title"));
        c.close ();
      }

      icon = getResources ().getDrawable (R.drawable.ic_rss);

      if (!isCat)
      {
        if (m_prefs.getBoolean ("download_feed_icons", false))
        {

          File iconFile = new File (getExternalCacheDir ().getAbsolutePath () +
            OfflineFeedsFragment.ICON_PATH + id + ".ico");

          if (iconFile.exists ())
          {
            final float scale = getResources ().getDisplayMetrics ().density;
            int size = (int) (20 * scale);
            icon = new BitmapDrawable (getResources (),
              Bitmap.createScaledBitmap (BitmapFactory.decodeFile (
              iconFile.getAbsolutePath ()), size, size, false));
          }
        }
      }
    }

    if (title == null)
    {
      title = getResources ().getString (R.string.app_name);
    }

    if (icon == null)
    {
      icon = getResources ().getDrawable (R.drawable.icon);
    }

    return new ActivityTitle (title, icon);
  }

  protected void updateActivityTitle (ActivityTitle at) {
    setTitle(at.getTitle ());
    getSupportActionBar().setIcon (at.getIcon ());

    activityTitle = at;
  }

  protected void storeFeedLang (int feedId, String lang) {
    SQLiteStatement stmtUpdateFeed = getWritableDb ().compileStatement (
      "UPDATE feeds SET lang = '" + lang +
      "' WHERE " + BaseColumns._ID + " = ?");
    stmtUpdateFeed.bindLong (1, feedId);
    stmtUpdateFeed.execute ();
    stmtUpdateFeed.close ();
  }

	protected Intent getShareIntent(Cursor article) {
		if (article != null) {
			String title = article.getString(article.getColumnIndex("title"));
			String link = article.getString(article.getColumnIndex("link"));

			Intent intent = new Intent(Intent.ACTION_SEND);

			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_SUBJECT, title);
			intent.putExtra(Intent.EXTRA_TEXT, link);

			return intent;
		} else {
			return null;
		}
	}

	protected void shareArticle(int articleId) {

		Cursor article = getArticleById(articleId);

		if (article != null) {
			shareArticle(article);
			article.close();
		}
	}

	private void shareArticle(Cursor article) {
		if (article != null) {
			Intent intent = getShareIntent(article);

			startActivity(Intent.createChooser(intent,
					getString(R.string.share_article)));
		}
	}

	protected int getSelectedArticleCount() {
		Cursor c = getReadableDb().query("articles",
				new String[] { "COUNT(*)" }, "selected = 1", null, null, null,
				null);
		c.moveToFirst();
		int selected = c.getInt(0);
		c.close();

		return selected;
	}

	protected int getUnreadArticleCount(int feedId, boolean isCat) {

		Cursor c;

		if (isCat) {
			c = getReadableDb().query("articles",
				new String[] { "COUNT(*)" }, "unread = 1 AND feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)",
				new String[] { String.valueOf(feedId) },
				null, null, null);
		} else {
			c = getReadableDb().query("articles",
				new String[] { "COUNT(*)" }, "unread = 1 AND feed_id = ?",
				new String[] { String.valueOf(feedId) },
				null, null, null);
		}

		c.moveToFirst();
		int selected = c.getInt(0);
		c.close();

		return selected;
	}

	protected void deselectAllArticles() {
		getWritableDb().execSQL("UPDATE articles SET selected = 0 ");
		refresh();
	}

	protected void refresh() {
		OfflineFeedsFragment ff = (OfflineFeedsFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_FEEDS);

		if (ff != null) {
			ff.refresh();
		}

		OfflineFeedCategoriesFragment cf = (OfflineFeedCategoriesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_CATS);

		if (cf != null) {
			cf.refresh();
		}

		OfflineHeadlinesFragment ohf = (OfflineHeadlinesFragment) getSupportFragmentManager()
				.findFragmentByTag(FRAG_HEADLINES);

		if (ohf != null) {
			ohf.refresh();
		}

		initMenu();
	}

	public void catchupFeed(int feedId, boolean isCat) {
		if (isCat) {
			SQLiteStatement stmt = getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, unread = 0 WHERE feed_id IN (SELECT "+
						BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");
			stmt.bindLong(1, feedId);
			stmt.execute();
			stmt.close();
		} else {
			SQLiteStatement stmt = getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, unread = 0 WHERE feed_id = ?");
			stmt.bindLong(1, feedId);
			stmt.execute();
			stmt.close();
		}

		refresh();
	}

	public void setLastContentImageHitTestUrl(String url) {
		m_lastImageHitTestUrl = url;
	}

	public String getLastContentImageHitTestUrl() {
		return m_lastImageHitTestUrl;
	}

	public void setViewMode(String viewMode) {
		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putString("offline_view_mode", viewMode);
		editor.commit();
	}

	public String getViewMode() {
		return m_prefs.getString("offline_view_mode", "adaptive");
	}

}
