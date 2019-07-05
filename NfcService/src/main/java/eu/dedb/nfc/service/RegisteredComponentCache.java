//package com.android.nfc;
/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//package com.android.nfc;
package eu.dedb.nfc.service;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A cache of intent filters registered to receive the TECH_DISCOVERED dispatch.
 */
public class RegisteredComponentCache {
    private static final String TAG = "RegisteredComponentCache";

    final Context mContext;
    final String mAction;
    final String mMetaDataName;
    final AtomicReference<BroadcastReceiver> mReceiver;

    // synchronized on this
    private ArrayList<ComponentInfo> mComponents;

    public RegisteredComponentCache(Context context, String action, String metaDataName) {
        mContext = context;
        mAction = action;
        mMetaDataName = metaDataName;

        generateComponentsList();

        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context1, Intent intent) {
                generateComponentsList();
            }
        };
        mReceiver = new AtomicReference<BroadcastReceiver>(receiver);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        mContext.registerReceiver(receiver, intentFilter);
        // Register for events related to sdcard installation.
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        mContext.registerReceiver(receiver, sdFilter);
    }

    public static class ComponentInfo {
        public final ResolveInfo resolveInfo;
        public final String[] techs;

        ComponentInfo(ResolveInfo resolveInfo, String[] techs) {
            this.resolveInfo = resolveInfo;
            this.techs = techs;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("ComponentInfo: ");
            out.append(resolveInfo);
            out.append(", techs: ");
            for (String tech : techs) {
                out.append(tech);
                out.append(", ");
            }
            return out.toString();
        }
    }

    /**
     * @return a collection of {@link RegisteredComponentCache.ComponentInfo} objects for all
     * registered authenticators.
     */
    public ArrayList<ComponentInfo> getComponents() {
        synchronized (this) {
            // It's safe to return a reference here since mComponents is always replaced and
            // never updated when it changes.
            return mComponents;
        }
    }

    /**
     * Stops the monitoring of package additions, removals and changes.
     */
    public void close() {
        final BroadcastReceiver receiver = mReceiver.getAndSet(null);
        if (receiver != null) {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (mReceiver.get() != null) {
            Log.e(TAG, "RegisteredServicesCache finalized without being closed");
        }
        close();
        super.finalize();
    }

    void dump(ArrayList<ComponentInfo> components) {
        for (ComponentInfo component : components) {
            Log.i(TAG, component.toString());
        }
    }

    void generateComponentsList() {
        PackageManager pm = mContext.getPackageManager();
        ArrayList<ComponentInfo> components = new ArrayList<ComponentInfo>();
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(new Intent(mAction),
                PackageManager.GET_META_DATA);
        for (ResolveInfo resolveInfo : resolveInfos) {
            try {
                parseComponentInfo(resolveInfo, components);
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Unable to load component info " + resolveInfo.toString(), e);
            } catch (IOException e) {
                Log.w(TAG, "Unable to load component info " + resolveInfo.toString(), e);
            }
        }

        dump(components);

        synchronized (this) {
            mComponents = components;
        }
    }

    void parseComponentInfo(ResolveInfo info, ArrayList<ComponentInfo> components)
            throws XmlPullParserException, IOException {
        ActivityInfo ai = info.activityInfo;
        PackageManager pm = mContext.getPackageManager();

        XmlResourceParser parser = null;
        try {
            parser = ai.loadXmlMetaData(pm, mMetaDataName);
            if (parser == null) {
                throw new XmlPullParserException("No " + mMetaDataName + " meta-data");
            }

            parseTechLists(pm.getResourcesForApplication(ai.applicationInfo), ai.packageName,
                    parser, info, components);
        } catch (NameNotFoundException e) {
            throw new XmlPullParserException("Unable to load resources for " + ai.packageName);
        } finally {
            if (parser != null) parser.close();
        }
    }

    void parseTechLists(Resources res, String packageName, XmlPullParser parser,
            ResolveInfo resolveInfo, ArrayList<ComponentInfo> components)
            throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.START_TAG) {
            eventType = parser.next();
        }

        ArrayList<String> items = new ArrayList<String>();
        String tagName;
        eventType = parser.next();
        do {
            tagName = parser.getName();
            if (eventType == XmlPullParser.START_TAG && "tech".equals(tagName)) {
                items.add(parser.nextText());
            } else if (eventType == XmlPullParser.END_TAG && "tech-list".equals(tagName)) {
                int size = items.size();
                if (size > 0) {
                    String[] techs = new String[size];
                    techs = items.toArray(techs);
                    items.clear();
                    components.add(new ComponentInfo(resolveInfo, techs));
                }
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);
    }
}