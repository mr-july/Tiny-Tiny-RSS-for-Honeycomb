package org.fox.ttrss;


import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import android.os.Parcel;
import android.os.Parcelable;


/**
 * this class is used to store activity title information (title string, icon,
 * etc.)
 */
public class ActivityTitle implements Parcelable
{

  /**
   * visible title string
   */
  private CharSequence title;
  /**
   * title icon
   */
  private Drawable icon;


  /**
   * public constructor with initialization
   *
   * @param title visible title string
   * @param icon title icon
   */
  public ActivityTitle (CharSequence title, Drawable icon)
  {
    this.title = title;
    this.icon = icon;
  }


  /**
   * public constructor with initialization from parcel
   *
   * @param source parcel to read data from
   */
  public ActivityTitle (Parcel source)
  {
    readFromParcel (source);
  }


  /**
   * fill object properties from parcel
   *
   * @param source parcel, containing data
   */
  public void readFromParcel (Parcel source)
  {
    title = source.readString ();

    // Deserialize Parcelable and cast to Bitmap first:
    Bitmap bitmap = (Bitmap) source.readParcelable (getClass ().getClassLoader ());
    // Convert Bitmap to Drawable:
    icon = new BitmapDrawable (bitmap);
  }


  /**
   * pack object properties to given parcel
   *
   * @param dest destination parcel
   * @param flags flags (unused)
   */
  public void writeToParcel (Parcel dest, int flags)
  {
    dest.writeString (title.toString ());

    // Convert Drawable to Bitmap first:
    Bitmap bitmap = (Bitmap) ((BitmapDrawable) icon).getBitmap ();
    dest.writeParcelable (bitmap, flags);
  }


  /**
   * get visible title string
   *
   * @return the title string
   */
  public CharSequence getTitle ()
  {
    return title;
  }


  /**
   * set visible title string
   *
   * @param title the title string to set
   */
  public void setTitle (CharSequence title)
  {
    this.title = title;
  }


  /**
   * title icon
   *
   * @return the icon
   */
  public Drawable getIcon ()
  {
    return icon;
  }


  /**
   * title icon
   *
   * @param icon the icon to set
   */
  public void setIcon (Drawable icon)
  {
    this.icon = icon;
  }


  public int describeContents ()
  {
    return 0;
  }
}
