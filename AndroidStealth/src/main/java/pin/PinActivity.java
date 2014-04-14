package pin;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.stealth.android.HomeActivity;
import com.stealth.android.R;
import com.stealth.utils.Utils;

public class PinActivity extends FragmentActivity implements PinFragment.OnPinResult {

	private PinFragment mPinFrag;

	/**
	 * Launches the pin dialog
	 * @param context the context to use for the launch
	 */
	public static void launch(Context context) {
		Intent pinIntent = new Intent(context, PinActivity.class);
		pinIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(pinIntent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_pin);
		Utils.setContext(getApplicationContext());

		// Check that the activity is using the layout version with
		// the fragment_container FrameLayout
		if (findViewById(R.id.container) != null) {

			// However, if we're being restored from a previous state,
			// then we don't need to do anything and should return or else
			// we could end up with overlapping fragments.
			if (savedInstanceState != null) {
				return;
			}

			// Create a new Fragment to be placed in the activity layout
			mPinFrag = PinFragment.newInstance(R.string.pin_title, R.string.pin_description_unlock, "");

			// Add the fragment to the 'fragment_container' FrameLayout
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, mPinFrag).commit();
		}
	}

	@Override
	public boolean onPinEntry(String pin) {
		mPinFrag.pinClear();
		if (HomeActivity.launch(getApplicationContext(), pin)) {
			finish();
			return true;
		}
		return false;
	}

	@Override
	public void onPinCancel() {
		finish();
	}
}
