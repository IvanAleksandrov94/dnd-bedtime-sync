package it.silleellie.dndsync;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class MainFragment extends PreferenceFragmentCompat {
    private Preference dndPref;
    private Preference bedtimePref;
    private Preference secureSettingsPref;
    private Preference powerSaverMode;



    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        dndPref = findPreference("dnd_permission_key");
        bedtimePref = findPreference("bedtime_key");
        secureSettingsPref = findPreference("secure_settings_permission_key");
        powerSaverMode = findPreference("power_saver_key");

        dndPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!checkDNDPermission()) {
                    Toast.makeText(getContext(), "Follow the instructions to grant the permission via ADB!", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        secureSettingsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!checkSecureSettingsPermission(getContext())) {
                    Toast.makeText(getContext(), "Follow the instructions to grant the permission via ADB!", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        checkDNDPermission();
        checkSecureSettingsPermission(getContext());
    }

    private boolean checkDNDPermission() {
        NotificationManager mNotificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        boolean allowed = mNotificationManager.isNotificationPolicyAccessGranted();
        if (allowed) {
            dndPref.setSummary(R.string.granted);
        } else {
            dndPref.setSummary(R.string.denied);
        }
        return allowed;
    }

    private boolean checkSecureSettingsPermission(Context context) {
        boolean allowed;
        allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
        if (allowed) {
            secureSettingsPref.setSummary(R.string.granted);
        } else {
            secureSettingsPref.setSummary(R.string.denied);
        }
        return allowed;
    }
}