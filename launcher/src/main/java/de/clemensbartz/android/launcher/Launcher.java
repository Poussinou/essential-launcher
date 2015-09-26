/*
 * Copyright (C) 2015  Clemens Bartz
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.clemensbartz.android.launcher;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.StrictMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ViewSwitcher;
import de.clemensbartz.android.launcher.adapters.DrawerListAdapter;
import de.clemensbartz.android.launcher.models.ApplicationModel;
import de.clemensbartz.android.launcher.models.DockUpdateModel;
import de.clemensbartz.android.launcher.models.HomeModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Launcher class.
 *
 * @author Clemens Bartz
 * @since 1.0
 */
public final class Launcher extends Activity {
    /** The TAG to identify the log messages of this class. */
    protected static final String TAG = "Launcher";
    /** The string for strict mode enabled. */
    protected static final String STRICT_MODE_ENABLED = "Strict mode enabled";

    /** Id to identify the home layout. */
    protected static final int HOME_ID = 0;
    /** Id to identify the launcher layout. */
    protected static final int DRAWER_ID = 1;

    /** Request code for picking a widget. */
    protected static final int REQUEST_PICK_APPWIDGET = 0;
    /** Request code for creating a widget. */
    protected static final int REQUEST_CREATE_APPWIDGET = 1;
    /** Request code for app info. */
    protected static final int ITEM_APPINFO = 1;
    /** Request code for app uninstall. */
    protected static final int ITEM_UNINSTALL = 2;

    /** The view switcher of the launcher. */
    private ViewSwitcher vsLauncher;
    /** The view for holding the widget. */
    private FrameLayout frWidget;
    /** The views for launching the most used apps. */
    private List<ImageView> dockImageViews = new ArrayList<>(HomeModel.NUMBER_OF_APPS);

    /** The model for home. */
    private HomeModel model;
    /** The manager for widgets. */
    private AppWidgetManager appWidgetManager;
    /** The host for widgets. */
    private AppWidgetHost appWidgetHost;
    /** The adapter for applications. */
    private DrawerListAdapter lvApplicationsAdapter;
    /** The list of installed applications. */
    private List<ApplicationModel> applicationModels = new ArrayList<>();
    /** The broadcast receiver for package changes. */
    private final PackageChangedBroadcastReceiver packageChangedBroadcastReceiver = new PackageChangedBroadcastReceiver();

    /** The drawable for the launcher. */
    private Drawable icLauncher;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launcher);



        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDialog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }

        /*
         * Assign components.
         */
        vsLauncher = (ViewSwitcher) findViewById(R.id.vsLauncher);
        frWidget = (FrameLayout) findViewById(R.id.frWidget);

        final GridView lvApplications = (GridView) findViewById(R.id.lvApplications);
        final ImageView ivDrawer = (ImageView) findViewById(R.id.ivDrawer);

        dockImageViews.add((ImageView) findViewById(R.id.ivDock1));
        dockImageViews.add((ImageView) findViewById(R.id.ivDock2));
        dockImageViews.add((ImageView) findViewById(R.id.ivDock3));
        dockImageViews.add((ImageView) findViewById(R.id.ivDock4));
        dockImageViews.add((ImageView) findViewById(R.id.ivDock5));
        dockImageViews.add((ImageView) findViewById(R.id.ivDock6));

        /*
         * Set handlers.
         */
        ivDrawer.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View view) {
                selectWidget();
                return true;
            }
        });

        lvApplications.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(
                    final AdapterView<?> adapterView,
                    final View view,
                    final int i,
                    final long l) {

                openApp(applicationModels.get(i));
            }
        });
        registerForContextMenu(lvApplications);
        lvApplications.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(
                    final ContextMenu contextMenu,
                    final View view,
                    final ContextMenu.ContextMenuInfo contextMenuInfo) {

                final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) contextMenuInfo;

                contextMenu.setHeaderTitle(applicationModels.get(info.position).getLabel());
                contextMenu.add(0, ITEM_APPINFO, 0, R.string.appinfo);

                // Check for system apps
                try {
                    ApplicationInfo ai = getPackageManager().getApplicationInfo(applicationModels.get(info.position).getPackageName(), 0);
                    if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0) {
                        contextMenu.add(0, ITEM_UNINSTALL, 1, R.string.uninstall);
                    }
                } catch (PackageManager.NameNotFoundException e) {

                }
            }
        });

        /*
         * Initialize data.
         */
        icLauncher = getDrawable(R.drawable.ic_launcher);

        model = new HomeModel(this);
        new LoadModelAsyncTask().execute();

        // Animate the image of the drawer button.
        final RippleDrawable rd = new RippleDrawable(ColorStateList.valueOf(Color.GRAY), ivDrawer.getDrawable(), null);
        ivDrawer.setImageDrawable(rd);

        // Initialize widget handling.
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, R.id.frWidget);
        appWidgetHost.startListening();

        // Load last widget lazily.
        LayoutInflater.from(this).inflate(R.layout.home_empty, frWidget);

        // Initialize applications adapter and set it.
        lvApplicationsAdapter = new DrawerListAdapter(this, lvApplications, R.layout.drawer_item, applicationModels);

        lvApplications.setAdapter(lvApplicationsAdapter);
    }

    @Override
    public void onBackPressed() {
        switchTo(HOME_ID);
    }

    @Override
    protected void onResume() {
        super.onResume();

        switchTo(HOME_ID);

        updateDock();
    }

    @Override
    protected void onPause() {
        super.onPause();

        new CloseDatabaseAsyncTask().execute();
    }

    @Override
    protected void onActivityResult(
            final int requestCode,
            final int resultCode,
            final Intent data) {

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                final Bundle extras = data.getExtras();
                final int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                final AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
                if (appWidgetInfo.configure != null) {
                    final Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                    intent.setComponent(appWidgetInfo.configure);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
                } else {
                    createWidget(data);
                }
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                createWidget(data);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        if (item.getItemId() == ITEM_APPINFO) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            startActivity(newAppDetailsIntent(applicationModels.get(info.position).getPackageName()));
        } else if (item.getItemId() == ITEM_UNINSTALL) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + applicationModels.get(info.position).getPackageName()));
            startActivity(intent);
        }

        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        appWidgetHost.stopListening();
        appWidgetHost = null;
    }

    /**
     * Open an app from the model.
     * @param applicationModel the model
     */
    public void openApp(final ApplicationModel applicationModel) {
        new LoadMostUsedAppsAsyncTask().execute(applicationModel);

        final ComponentName component = new ComponentName(applicationModel.getPackageName(), applicationModel.getClassName());
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(component);

        startActivity(intent);
    }

    /**
     * Open the drawer.
     * @param view the originating view
     */
    public void openDrawer(final View view) {
        switchTo(DRAWER_ID);
    }

    /**
     * Switch to a layout.
     *
     * @param id the id of the layout
     * @return if the layout has been switched
     */
    private boolean switchTo(final int id) {
        switch (vsLauncher.getDisplayedChild()) {
            case HOME_ID:
                if (id == DRAWER_ID) {
                    final IntentFilter filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
                    filter.addAction(Intent.ACTION_INSTALL_PACKAGE);
                    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
                    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
                    filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
                    filter.addDataScheme("package");

                    registerReceiver(packageChangedBroadcastReceiver, filter);

                    updateApplications();

                    vsLauncher.showNext();

                    return true;
                }
                return false;
            case DRAWER_ID:
                if (id == HOME_ID) {
                    unregisterReceiver(packageChangedBroadcastReceiver);

                    vsLauncher.showPrevious();

                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * Trigger an intent to select a widget.
     */
    private void selectWidget() {
        final int appWidgetId = appWidgetHost.allocateAppWidgetId();

        final Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        pickIntent.putExtra(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, frWidget.getHeight());
        pickIntent.putExtra(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, frWidget.getWidth());
        pickIntent.putExtra(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        pickIntent.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, new ArrayList<Parcelable>());
        pickIntent.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, new ArrayList<Parcelable>());

        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }

    /**
     * Create a widget from an intent.
     * @param intent the intent
     */
    private void createWidget(final Intent intent) {
        final Bundle extras = intent.getExtras();

        if (model.getAppWidgetId() > -1) {
            appWidgetHost.deleteAppWidgetId(model.getAppWidgetId());
            frWidget.removeAllViews();
        }

        final int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        model.setAppWidgetId(appWidgetId);

        addHostView(appWidgetId);
    }

    /**
     * Add a host view to the frame layout for a widget id.
     * @param appWidgetId the widget id
     */
    private void addHostView(final int appWidgetId) {
        frWidget.removeAllViews();

        final AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo != null) {
            final AppWidgetHostView hostView = appWidgetHost.createView(this, appWidgetId, appWidgetInfo);
            hostView.setAppWidget(appWidgetId, appWidgetInfo);

            frWidget.addView(hostView);
        } else {
            model.setAppWidgetId(-1);

            LayoutInflater.from(this).inflate(R.layout.home_empty, frWidget);
        }
    }

    /**
     * Update installed applications.
     */
    private void updateApplications() {
        new UpdateAsyncTask().execute();
    }

    /**
     * Update dock.
     */
    private void updateDock() {
        new LoadMostUsedAppsAsyncTask().execute();
    }

    /**
     * Handle click on dock image.
     * @param imageView the image view that was clicked
     */
    private void onDockClick(final ImageView imageView) {
        if (imageView.getTag() != null) {
            if (imageView.getTag() instanceof ApplicationModel) {
                openApp((ApplicationModel) imageView.getTag());
            }
        }
    }
    /**
     * Update the dock image to feature the application model.
     * @param imageView the view
     * @param applicationModel the model, can be <code>null</code>
     */
    private void updateDock(final ImageView imageView, final ApplicationModel applicationModel) {
        if (applicationModel == null) {
            if (imageView.getTag() != null) {
                imageView.setTag(null);
                imageView.setImageDrawable(icLauncher);
                imageView.setOnClickListener(null);
                imageView.setContentDescription(null);
            }
        } else {
            final Object tag = imageView.getTag();

            if (tag != null) {
                if (tag instanceof ApplicationModel) {
                    final ApplicationModel tagModel = (ApplicationModel) tag;

                    if (tagModel.getPackageName().equals(applicationModel.getPackageName())
                            && tagModel.getClassName().equals(applicationModel.getClassName())
                            && tagModel.getLabel().equals(applicationModel.getLabel())
                    ) {
                        return;
                    }
                }
            }

            final RippleDrawable rd = new RippleDrawable(ColorStateList.valueOf(Color.GRAY), applicationModel.getIcon(), null);
            imageView.setImageDrawable(rd);
            imageView.setTag(applicationModel);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    if (view instanceof ImageView) {
                        onDockClick((ImageView) view);
                    }
                }
            });
            imageView.setContentDescription(applicationModel.getLabel());
        }
    }

    /**
     * <p>Intent to show an applications details page in (Settings) com.android.settings.</p>
     *
     * @param packageName   The package name of the application
     * @return the intent to open the application info screen.
     */
    public static Intent newAppDetailsIntent(final String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            final Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("package:" + packageName));
            return intent;
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.FROYO) {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName("com.android.settings",
                    "com.android.settings.InstalledAppDetails");
            intent.putExtra("pkg", packageName);
            return intent;
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName("com.android.settings",
                "com.android.settings.InstalledAppDetails");
        intent.putExtra("com.android.settings.ApplicationPkgName", packageName);
        return intent;
    }

    /**
     * Broadcast receiver for package change.
     */
    private final class PackageChangedBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            updateApplications();
        }
    }

    /**
     * Async task for closing the database.
     */
    private class CloseDatabaseAsyncTask extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer doInBackground(final Integer... chars) {
            model.close();
            return 0;
        }
    }

    /**
     * Async task for loading the most used applications.
     */
    private class LoadMostUsedAppsAsyncTask extends AsyncTask<ApplicationModel, DockUpdateModel, Integer> {
        @Override
        protected Integer doInBackground(final ApplicationModel... applicationModels) {
            for (ApplicationModel applicationModel : applicationModels) {
                model.addUsage(applicationModel.getPackageName(), applicationModel.getClassName());
            }

            if (applicationModels.length == 0) {
                model.updateApplications();
            }

            final List<ApplicationModel> mostUsedApplications = model.getMostUsedApplications();

            for (int i = 0; i < dockImageViews.size(); i++) {
                if (i >= mostUsedApplications.size()) {
                    publishProgress(new DockUpdateModel(dockImageViews.get(i), null));
                } else {
                    publishProgress(new DockUpdateModel(dockImageViews.get(i), mostUsedApplications.get(i)));
                }
            }

            return 0;
        }

        @Override
        protected void onProgressUpdate(final DockUpdateModel... values) {
            for (DockUpdateModel dockUpdateModel : values) {
                updateDock(dockUpdateModel.getImageView(), dockUpdateModel.getApplicationModel());
            }
        }
    }

    /**
     * Async task for loading the model on start.
     */
    private class LoadModelAsyncTask extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer doInBackground(final Integer... integers) {
            model.loadValues();

            return model.getAppWidgetId();
        }

        @Override
        protected void onPostExecute(final Integer integer) {
            // Show last selected widget.
            if (integer > -1) {
                addHostView(integer);
            }
        }
    }

    /**
     * Async task to update applications of the list view.
     */
    private class UpdateAsyncTask extends AsyncTask<Integer, Integer, Integer> {

        /** Number of apps after which a refresh should be triggered. */
        protected static final int REFRESH_NUMBER = 5;

        @Override
        protected Integer doInBackground(final Integer... integers) {
            final Intent intent = new Intent();
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            final PackageManager pm = getPackageManager();
            final List<ResolveInfo> resolveInfos =
                    pm.queryIntentActivities(intent, 0);
            Collections.sort(
                    resolveInfos,
                    new ResolveInfo.DisplayNameComparator(pm)
            );

            int i = 0;

            for (ResolveInfo resolveInfo : resolveInfos) {
                i = i + 1;
                applicationModels.add(new ApplicationModel(resolveInfo.loadLabel(pm), resolveInfo.loadIcon(pm), resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
                if (i % REFRESH_NUMBER == 0) {
                    publishProgress();
                }
            }

            return 0;
        }

        @Override
        protected void onPostExecute(final Integer integer) {
            lvApplicationsAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onPreExecute() {
            applicationModels.clear();
            lvApplicationsAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onProgressUpdate(final Integer... values) {
            lvApplicationsAdapter.notifyDataSetChanged();
        }
    }
}