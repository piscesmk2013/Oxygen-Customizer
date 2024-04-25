package it.dhd.oxygencustomizer.xposed.hooks.launcher;

import static android.content.Context.RECEIVER_EXPORTED;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.utils.Constants;
import it.dhd.oxygencustomizer.xposed.XposedMods;

public class Launcher extends XposedMods {

    private static final String listenPackage = Constants.Packages.LAUNCHER;

    private int mFolderRows, mFolderColumns, mDrawerColumns;
    private boolean mFolderRearrange = false, mFolderPreview = false, mDrawerRearrange = false, mOpenAppDetails;
    private boolean mRemoveShortcut = false, mRemoveClone = false;
    private boolean mRemoveFolderPagination = false, mRemoveHomePagination = false;

    public Launcher(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        mFolderRows = Xprefs.getSliderInt("folder_rows", 3);
        mFolderColumns = Xprefs.getSliderInt("folder_columns", 3);
        mDrawerColumns = Xprefs.getSliderInt("drawer_columns", 4);
        mFolderRearrange = Xprefs.getBoolean("rearrange_folder", true);
        mFolderPreview = Xprefs.getBoolean("rearrange_preview", true);
        mDrawerRearrange = Xprefs.getBoolean("rearrange_drawer", true);
        mOpenAppDetails = Xprefs.getBoolean("launcher_open_app_details", false);
        mRemoveShortcut = Xprefs.getBoolean("remove_shortcut_badge_title", false);
        mRemoveClone = Xprefs.getBoolean("remove_clone_badge", false);
        mRemoveFolderPagination = Xprefs.getBoolean("remove_folder_pagination", false);
        mRemoveHomePagination = Xprefs.getBoolean("remove_home_pagination", false);

    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(listenPackage)) return;

        findAndHookConstructor("com.android.launcher3.InvariantDeviceProfile$GridOption", lpparam.classLoader, Context.class, AttributeSet.class, int.class, new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                log("InvariantDeviceProfile$GridOption constructor called");
                if (mFolderRearrange) {
                    XposedHelpers.setIntField(param.thisObject, "numFolderColumns", mFolderColumns);
                    XposedHelpers.setIntField(param.thisObject, "numFolderRows", mFolderRows);
                }
                if (mFolderPreview) if (mFolderColumns > 3) XposedHelpers.setIntField(param.thisObject, "numFolderPreview", mFolderColumns);
                if (mDrawerRearrange) XposedHelpers.setIntField(param.thisObject, "numAllAppsColumns", mDrawerColumns);
            }
        });

        Class<?> OplusTaskViewImpl = findClass("com.android.quickstep.views.OplusTaskViewImpl", lpparam.classLoader);

        hookAllMethods(OplusTaskViewImpl, "setIcon", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View headerView = (View) callMethod(param.thisObject, "getHeaderView");
                View iconView = (View) callMethod(headerView, "getTaskIcon");
                View titleView = (View) callMethod(headerView, "getTitleTv");
                Object task = callMethod(param.thisObject, "getTask");
                if (task == null) return;
                Object key = getObjectField(task, "key");
                if (key == null) return;
                String pkgName = (String) callMethod(key, "getPackageName");
                int userId = getIntField(key, "userId");
                final ClickListener clickListener = new ClickListener(pkgName, userId);

                iconView.setOnLongClickListener(clickListener);
                titleView.setOnLongClickListener(clickListener);
            }
        });

        Class<?> DockIconView = findClass("com.oplus.quickstep.dock.DockIconView", lpparam.classLoader);
        findAndHookMethod(DockIconView, "setIcon", Drawable.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object task = callMethod(param.thisObject, "getTask");
                if (task == null) return;
                Object key = getObjectField(task, "key");
                if (key == null) return;
                String pkgName = (String) callMethod(key, "getPackageName");
                int userId = getIntField(key, "userId");
                View iconView = (View) param.thisObject;
                final ClickListener clickListener = new ClickListener(pkgName, userId);
                iconView.setOnLongClickListener(clickListener);
            }
        });

        Class<?> OplusPageIndicator = findClass("com.android.launcher.pageindicators.OplusPageIndicator", lpparam.classLoader);
        findAndHookMethod(OplusPageIndicator, "onDraw", Canvas.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View v = (View) param.thisObject;
                switch (v.getParent().getClass().getCanonicalName()) {
                    case "com.android.launcher3.OplusDragLayer":
                        v.setVisibility(View.GONE);
                        if (mRemoveHomePagination) param.setResult(null);
                        break;
                    case "android.widget.FrameLayout":
                        v.setVisibility(View.GONE);
                        if (mRemoveFolderPagination) param.setResult(null);
                        break;
                }
            }
        });

        try {
            Class<?> PageIndicatorTouchHelper = findClass("com.android.launcher.pageindicators.PageIndicatorTouchHelper", lpparam.classLoader);
            findAndHookMethod(PageIndicatorTouchHelper, "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mRemoveHomePagination) param.setResult(false);
                }
            });
        } catch (Throwable ignored) {}

    }

    class ClickListener implements View.OnLongClickListener {

        final String pkgName;
        final int userId;

        public ClickListener(String pkgName, int userId) {
            this.pkgName = pkgName;
            this.userId = userId;
        }

        @Override
        public boolean onLongClick(View v) {
            if (!mOpenAppDetails) return false;
            Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkgName, null));
            appDetails.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appDetails.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            appDetails.putExtra("userId", userId);
            mContext.startActivity(appDetails);
            return true;
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
