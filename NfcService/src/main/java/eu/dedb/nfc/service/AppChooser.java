package eu.dedb.nfc.service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AppChooser extends Activity implements OnItemClickListener,
		OnItemLongClickListener {

	public static final String EXTRA_RESOLVE_INFOS = "rlist";
	private static final int PICK_ACTIVITY_REQUEST = 0;
	private static final int TARGET_ACTIVITY_REQUEST = 1;

	private Intent targetIntent;
	private static ComponentName targetComponentName = null;

	private PackageManager mPackageManager;
	private LocalBroadcastManager mLocalBroadcastManager;

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			if (NfcService.ACTION_DISPATCH_TAG.equals(intent.getAction())
					&& targetComponentName != null) {
				Intent targetIntent = intent
						.getParcelableExtra(Intent.EXTRA_INTENT);

				targetIntent.setComponent(targetComponentName);
				targetIntent.addFlags(0
				// | Intent.FLAG_ACTIVITY_SINGLE_TOP
				// | Intent.FLAG_ACTIVITY_CLEAR_TOP
						| Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				startActivity(targetIntent);
			}
		}
	};

	public static boolean isActive() {
		return targetComponentName != null;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (intent != null
				&& (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
			finish();
		}

		mPackageManager = getApplicationContext().getPackageManager();

		// Get LocalBroadcastManager
		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

		// Register local Broadcast Receiver
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(NfcService.ACTION_DISPATCH_TAG);
		mLocalBroadcastManager.registerReceiver(mBroadcastReceiver,
				intentFilter);

		this.getWindow().setBackgroundDrawable(
				new ColorDrawable(Color.parseColor("#7F000000")));

		if (NfcService.ACTION_DISPATCH_TAG.equals(intent.getAction())) {

			targetIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);

			if (targetIntent == null) {
				finish();
				return;
			}

			ArrayList<ResolveInfo> rList = intent
					.getParcelableArrayListExtra(AppChooser.EXTRA_RESOLVE_INFOS);

			if (rList == null || rList.isEmpty()) {
				if (targetIntent != null) {
					Intent pickerIntent = new Intent(
							Intent.ACTION_PICK_ACTIVITY);
					pickerIntent.putExtra(Intent.EXTRA_INTENT, targetIntent);
					startActivityForResult(pickerIntent, PICK_ACTIVITY_REQUEST);
				}
			} else if (rList.size() == 1) {
				ResolveInfo rInfo = (ResolveInfo) rList.get(0);
				targetComponentName = new ComponentName(
						rInfo.activityInfo.packageName, rInfo.activityInfo.name);
				targetIntent.setComponent(targetComponentName);
				startActivityForResult(targetIntent, TARGET_ACTIVITY_REQUEST);
			} else {
				setContentView(R.layout.app_chooser_layout);
				GridView gridView = (GridView) findViewById(R.id.gridView1);
				gridView.setAdapter(new ResolveInfoAdapter(this, rList));
				gridView.setOnItemClickListener(this);
				gridView.setOnItemLongClickListener(this);
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent targetIntent) {
		super.onActivityResult(requestCode, resultCode, targetIntent);
		if (requestCode == PICK_ACTIVITY_REQUEST) {
			if (targetIntent != null) {
				targetComponentName = targetIntent.getComponent();

				targetIntent.setFlags(targetIntent.getFlags()
						& ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
				// targetIntent.setFlags(0);
				targetIntent.addFlags(0 | Intent.FLAG_ACTIVITY_NEW_TASK
				// | Intent.FLAG_ACTIVITY_SINGLE_TOP
				// | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
				// | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
				// | Intent.FLAG_ACTIVITY_TASK_ON_HOME
						);
				startActivity(targetIntent);
				// startActivityForResult(targetIntent,
				// TARGET_ACTIVITY_REQUEST);

			} else {
				finish();
			}
		} else if (requestCode == TARGET_ACTIVITY_REQUEST) {
			finish();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		ResolveInfo rInfo = (ResolveInfo) parent.getAdapter().getItem(position);
		try {
			targetComponentName = new ComponentName(rInfo.activityInfo.packageName,
					rInfo.activityInfo.name);
			targetIntent.setComponent(targetComponentName);
			startActivityForResult(targetIntent, TARGET_ACTIVITY_REQUEST);
		} catch (SecurityException e) {
			//targetComponentName = getPackageManager().getLaunchIntentForPackage(rInfo.activityInfo.packageName).getComponent();
			//targetIntent.setComponent(targetComponentName);
			Intent intent = getPackageManager().getLaunchIntentForPackage(rInfo.activityInfo.packageName);
			//intent.setAction(targetIntent.getAction());
			intent.setComponent(targetComponentName);
			//intent.putExtra(NfcAdapter.EXTRA_TAG, targetIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG));
			//startActivity(intent);
			startActivityForResult(intent, TARGET_ACTIVITY_REQUEST);
		}

	}

	@Override
	protected void onDestroy() {
		ExitActivity.exitApplicationAnRemoveFromRecent(this);
		mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
		targetComponentName = null;
		super.onDestroy();
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view,
			int position, long id) {
		ResolveInfo rInfo = (ResolveInfo) parent.getAdapter().getItem(position);
		Toast.makeText(getApplicationContext(), rInfo.activityInfo.packageName,
				Toast.LENGTH_SHORT).show();
		// TODO Open package info
		return true;

	}

	class ResolveInfoAdapter extends ArrayAdapter<ResolveInfo> {

		private final ArrayList<ResolveInfo> values;
		private LayoutInflater mInflater;

		public ResolveInfoAdapter(Context context, ArrayList<ResolveInfo> values) {
			super(context, R.layout.resolve_grid_item, values);

			@SuppressWarnings("deprecation")
			final Collator mCollator = Collator.getInstance(context
					.getResources().getConfiguration().locale);
			Collections.sort(values, new Comparator<ResolveInfo>() {
				@Override
				public int compare(ResolveInfo o1, ResolveInfo o2) {
					CharSequence l1 = o1.loadLabel(mPackageManager);
					if (l1 == null)
						l1 = o1.activityInfo.name;
					CharSequence l2 = o2.loadLabel(mPackageManager);
					if (l2 == null)
						l2 = o2.activityInfo.name;
					return mCollator.compare(l1, l2);
				}
			});
			this.values = values;
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.resolve_grid_item,
						null);
				holder = new ViewHolder();
				holder.imageView1 = (ImageView) convertView
						.findViewById(R.id.icon);
				holder.textView1 = (TextView) convertView
						.findViewById(R.id.text1);
				holder.textView2 = (TextView) convertView
						.findViewById(R.id.text2);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			Drawable icon = values.get(position).loadIcon(mPackageManager);
			String name = (String) values.get(position).loadLabel(
					mPackageManager);
			String packageName = null;
			int count = 0;
			for (ResolveInfo value : values) {
				String label = (String) value.loadLabel(mPackageManager);
				if (label == null)
					label = value.activityInfo.packageName;
				if (name.equals(label))
					count++;
				if (count > 1) {
					packageName = values.get(position).activityInfo.packageName;
					break;
				}
			}
			holder.imageView1.setImageDrawable(icon);
			holder.textView1.setText(name);
			if (packageName != null) {
				holder.textView2.setVisibility(View.VISIBLE);
				holder.textView2.setText(packageName);
			} else {
				holder.textView2.setVisibility(View.GONE);
			}
			return convertView;
		}

		public class ViewHolder {
			public ImageView imageView1;
			public TextView textView1;
			public TextView textView2;
		}
	}

}
