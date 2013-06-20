package org.fox.ttrss;


import android.graphics.drawable.Drawable;


/**
 * this class is used to store activity title information (title string, icon,
 * etc.)
 */
public class ActivityTitle
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
}
