package eu.dedb.nfc.lib;

import android.nfc.Tag;
import android.os.IBinder;

public abstract class PICC implements AndroidNfcTag {
	abstract public int getHandle();

	abstract public Tag getTag(IBinder tagService);
}
