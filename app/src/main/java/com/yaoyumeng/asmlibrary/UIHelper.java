
package com.yaoyumeng.asmlibrary;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Ӧ�ó���UI���߰�����װUI��ص�һЩ����
 * 
 * @author liux (http://my.oschina.net/liux)
 * @version 1.0
 * @created 2012-3-21
 */
public class UIHelper {

	public final static int LISTVIEW_ACTION_INIT = 0x01;
	public final static int LISTVIEW_ACTION_REFRESH = 0x02;
	public final static int LISTVIEW_ACTION_SCROLL = 0x03;
	public final static int LISTVIEW_ACTION_CHANGE_CATALOG = 0x04;

	public final static int LISTVIEW_DATA_MORE = 0x01;
	public final static int LISTVIEW_DATA_LOADING = 0x02;
	public final static int LISTVIEW_DATA_FULL = 0x03;
	public final static int LISTVIEW_DATA_EMPTY = 0x04;

	public final static int LISTVIEW_DATATYPE_NEWS = 0x01;
	public final static int LISTVIEW_DATATYPE_BLOG = 0x02;
	public final static int LISTVIEW_DATATYPE_POST = 0x03;
	public final static int LISTVIEW_DATATYPE_TWEET = 0x04;
	public final static int LISTVIEW_DATATYPE_ACTIVE = 0x05;
	public final static int LISTVIEW_DATATYPE_MESSAGE = 0x06;
	public final static int LISTVIEW_DATATYPE_COMMENT = 0x07;

	public final static int REQUEST_CODE_FOR_RESULT = 0x01;
	public final static int REQUEST_CODE_FOR_REPLY = 0x02;

	/** ����ͼƬƥ�� */
	private static Pattern facePattern = Pattern.compile("\\[{1}([0-9]\\d*)\\]{1}");

	/** ȫ��web��ʽ */
	public final static String WEB_STYLE = "<style>* {font-size:16px;line-height:20px;} p {color:#333;} a {color:#3E62A6;} img {max-width:310px;} "
			+ "img.alignleft {float:left;max-width:120px;margin:0 10px 5px 0;border:1px solid #ccc;background:#fff;padding:2px;} "
			+ "pre {font-size:9pt;line-height:12pt;font-family:Courier New,Arial;border:1px solid #ddd;border-left:5px solid #6CE26C;background:#f6f6f6;padding:5px;} "
			+ "a.tag {font-size:15px;text-decoration:none;background-color:#bbd6f3;border-bottom:2px solid #3E6D8E;border-right:2px solid #7F9FB6;color:#284a7b;margin:2px 2px 2px 0;padding:2px 4px;white-space:nowrap;}</style>";

	private static WeakReference<Toast> prevToast = new WeakReference<Toast>(null);
	private static WeakReference<Context> prevContext = new WeakReference<Context>(null);

	/**
	 * ��ʾ��ҳ
	 * 
	 * @param activity
	 */
	public static void showHome(Activity activity) {
		Intent intent = new Intent(activity, ASMLibraryActivity.class);
		activity.startActivity(intent);
		activity.finish();
	}

	/**
	 * ����Toast��Ϣ
	 * 
	 * @param msg
	 */
	public static void showToast(Context cont, String msg) {
		showToast(cont, msg, Toast.LENGTH_SHORT);
	}

	public static void showToast(Context cont, int msg) {
		showToast(cont, cont.getString(msg), Toast.LENGTH_SHORT);
	}

	public static void showToast(Context cont, int msg, int time) {
		showToast(cont, cont.getString(msg), time);
	}

	public static void showToast(Context cont, String msg, int time) {
		if (prevContext.get() != null && prevToast.get() != null && prevContext.get() == cont) {
			Toast toast = prevToast.get();
			toast.setText(msg);
			toast.setDuration(time);
			toast.show();
			return;
		}
		Toast toast = Toast.makeText(cont, msg, time);
		toast.show();
		prevContext = new WeakReference<Context>(cont);
		prevToast = new WeakReference<Toast>(toast);
	}

	/**
	 * ������ؼ����¼�
	 * 
	 * @param activity
	 * @return
	 */
	public static View.OnClickListener finish(final Activity activity) {
		return new View.OnClickListener() {
			public void onClick(View v) {
				activity.finish();
			}
		};
	}

	public static void exitAppOnKeyBack(final Context cont) {
		if (ExitHelper.exitHelper.isShouldExit()) {
			ExitHelper.exitHelper.reset();
			try {
				Activity activity = (Activity)cont;
				activity.finish();
				ActivityManager activityMgr = (ActivityManager) cont
						.getSystemService(Context.ACTIVITY_SERVICE);
				activityMgr.restartPackage(cont.getPackageName());
				System.exit(0);
			} catch (Exception e) {
			}
		} else {
			showToast(cont, "Click again to exit");
		}
	}

	/**
	 * �˳����֡����ڷ��ؼ����ٰ�һ���˳���
	 * 
	 * @author Geek_Soledad <a target="_blank" href=
	 *         "http://mail.qq.com/cgi-bin/qm_share?t=qm_mailme&email=XTAuOSVzPDM5LzI0OR0sLHM_MjA"
	 *         style="text-decoration:none;"><img src=
	 *         "http://rescdn.qqmail.com/zh_CN/htmledition/images/function/qm_open/ico_mailme_01.png"
	 *         /></a>
	 */
	public static class ExitHelper {
		protected static ExitHelper exitHelper = new ExitHelper();
		private long startTime = -1;

		private ExitHelper() {
		}

		public boolean isShouldExit() {
			long currentTime = System.currentTimeMillis();
			if (currentTime - startTime > 2000) {
				startTime = currentTime;
				return false;
			} else {
				return true;
			}
		}

		public void reset() {
			startTime = -1;
		}
	}
}
