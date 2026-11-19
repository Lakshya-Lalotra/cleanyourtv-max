package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Home's own Settings, in Home's look: a list of areas on the left (Wi-Fi, Bluetooth, Sound, Apps, Storage, About and
 * More) and the selected one's page beside it on glass. It does what Android lets an app do: show the network and
 * nearby ones and save a new one (Android asks once), switch Bluetooth and list its devices, set the volume, list,
 * open and uninstall apps, show storage and the TV's details. What only Android's Settings can change (picture, date,
 * pairing a new device) opens there, from a row that says so. Drawn over the wallpaper like search; Back steps out.
 */
@SuppressLint("MissingPermission")
final class SettingsPanel {
    interface Host {
        void onSettingsShown(boolean shown, boolean animate);

        void onSettingsClosed();

        /** Launcher settings, which Home opens itself (it rebuilds when they close). */
        void openLauncherSettings();
    }

    static final int WIFI = 0, BLUETOOTH = 1, SOUND = 2, APPS = 3, STORAGE = 4, ABOUT = 5, MORE = 6;
    private static final String[] NAMES = {"Wi-Fi", "Bluetooth", "Sound", "Apps", "Storage", "About", "More settings"};
    private static final int[] ICONS = {R.drawable.ic_wifi, R.drawable.ic_bluetooth, R.drawable.ic_volume,
            R.drawable.ic_apps, R.drawable.ic_storage, R.drawable.ic_info, R.drawable.ic_tune};

    private final Context context;
    private final Host host;
    private final float density;
    private final View scrim;
    private final FrameLayout layer;
    private final List<View> categories = new ArrayList<>();
    private final TextView pageTitle, pageNote;
    private final LinearLayout pageRows;
    private final ScrollView scroller;
    /** The page's focusable rows, top to bottom; Up and Down walk them. */
    private final List<View> rows = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean opened, closed, receiversOn;
    private int category = WIFI;
    /** A page within the page: an app's actions, or joining a network. Back returns to the page. */
    private Runnable subBack;

    SettingsPanel(Context context, FrameLayout root, Host host) {
        this.context = context;
        this.host = host;
        density = context.getResources().getDisplayMetrics().density;

        scrim = new View(context);
        scrim.setBackgroundColor(0xFF000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        layer = new FrameLayout(context);
        layer.setAlpha(0f);
        layer.setVisibility(View.GONE);
        root.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The areas, on the left.
        LinearLayout side = column();
        side.setPadding(dp(60), dp(40), 0, dp(24));
        TextView heading = text(30, 0xFFFFFFFF, R.string.font_light);
        heading.setText("Settings");
        heading.setPadding(dp(14), 0, 0, dp(22));
        side.addView(heading);
        for (int i = 0; i < NAMES.length; i++) {
            View row = categoryRow(i);
            categories.add(row);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(240), dp(44));
            lp.bottomMargin = dp(4);
            side.addView(row, lp);
        }
        layer.addView(side, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The page, on glass, to the right.
        LinearLayout page = column();
        page.setPadding(dp(26), dp(22), dp(26), dp(18));
        page.setBackground(glass(0x14FFFFFF, 0x29FFFFFF, 24));
        pageTitle = text(24, 0xFFFFFFFF, R.string.font_light);
        pageNote = text(13, 0x8CFFFFFF, R.string.font_regular);
        pageNote.setPadding(0, dp(6), 0, dp(12));
        pageNote.setSingleLine(false);
        page.addView(pageTitle);
        page.addView(pageNote);
        pageRows = column();
        scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFocusable(false);
        scroller.addView(pageRows);
        page.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.MATCH_PARENT);
        plp.setMarginStart(dp(330));
        plp.topMargin = dp(40);
        plp.bottomMargin = dp(36);
        layer.addView(page, plp);
    }

    boolean isOpen() {
        return opened;
    }

    /** Opens on an area, with focus on its first row. */
    void open(int area) {
        if (closed) return;
        if (!opened) {
            opened = true;
            host.onSettingsShown(true, true);
            scrim.setVisibility(View.VISIBLE);
            scrim.animate().alpha(0.78f).setDuration(240).start();
            layer.setVisibility(View.VISIBLE);
            layer.setTranslationY(-dp(16));
            layer.animate().alpha(1f).translationY(0f).setDuration(240).start();
            listen(true);
            audioAsked = false;   // speakers and headphones asked again each time Settings opens
            audioConnected.clear();
        }
        category = area;
        subBack = null;
        showPage(area);
        if (!rows.isEmpty()) rows.get(0).requestFocus();
        else categories.get(area).requestFocus();
    }

    void close() {
        if (!opened) return;
        hideKeyboard();
        opened = false;
        listen(false);
        scrim.animate().alpha(0f).setDuration(200).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        layer.animate().alpha(0f).translationY(-dp(16)).setDuration(180)
                .withEndAction(() -> layer.setVisibility(View.GONE)).start();
        host.onSettingsShown(false, true);
        host.onSettingsClosed();
    }

    void closeNow() {
        if (!opened) return;
        hideKeyboard();
        opened = false;
        listen(false);
        scrim.animate().cancel();
        layer.animate().cancel();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        layer.setAlpha(0f);
        layer.setVisibility(View.GONE);
        host.onSettingsShown(false, false);
    }

    void shutdown() {
        closeNow();
        closed = true;
    }

    /** Back from one of Android's screens: the page again, as things may have changed there. */
    void refresh() {
        if (!opened || subBack != null) return;
        int focused = focusedRow();
        boolean onSide = focused < 0;
        showPage(category);
        if (onSide) categories.get(category).requestFocus();
        else if (!rows.isEmpty()) rows.get(Math.min(focused, rows.size() - 1)).requestFocus();
    }

    private int focusedRow() {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).hasFocus()) return i;
        return -1;
    }

    // ---- broadcasts: Wi-Fi scans and Bluetooth switching redraw their page -----------------------

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (!opened || subBack != null) return;
            boolean wifi = WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction());
            if (wifi && category == WIFI || !wifi && category == BLUETOOTH) refresh();
        }
    };

    private void listen(boolean on) {
        if (on == receiversOn) return;
        receiversOn = on;
        if (on) {
            IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(changes, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(changes, f);
            }
        } else {
            try {
                context.unregisterReceiver(changes);
            } catch (IllegalArgumentException ignored) {
                // not registered
            }
        }
    }

    // ---- the areas -------------------------------------------------------------------------------

    private View categoryRow(int index) {
        LinearLayout row = row();
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setFocusable(true);
        row.setDefaultFocusHighlightEnabled(false);
        ImageView icon = new ImageView(context);
        icon.setImageResource(ICONS[index]);
        row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView name = text(16, 0xFFFFFFFF, R.string.font_medium);
        name.setText(NAMES[index]);
        name.setPadding(dp(12), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        GradientDrawable focus = glass(0xF5FFFFFF, 0xF5FFFFFF, 14);
        GradientDrawable chosen = glass(0x24FFFFFF, 0x00000000, 14);
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? focus : index == category ? chosen : null);
            int ink = has ? 0xFF111111 : 0xFFFFFFFF;
            name.setTextColor(ink);
            icon.setColorFilter(ink);
            if (has && index != category) {
                category = index;
                subBack = null;
                for (View other : categories) if (other != v) other.setBackground(null);
                // Shown after a short pause, so running down the list does not build every page on the way.
                handler.removeCallbacks(showSelected);
                handler.postDelayed(showSelected, 120);
            }
        });
        row.setOnKeyListener((v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (index > 0) categories.get(index - 1).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (index < categories.size() - 1) categories.get(index + 1).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    handler.removeCallbacks(showSelected);
                    if (rows.isEmpty() || pageShown != category) showPage(category);
                    if (!rows.isEmpty()) rows.get(0).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    close();
                    return true;
                default:
                    return false;
            }
        });
        return row;
    }

    private int pageShown = -1;
    private final Runnable showSelected = () -> {
        if (opened && pageShown != category) showPage(category);
    };

    private void showPage(int area) {
        pageShown = area;
        hideKeyboard();
        // A focused row is about to go: park focus on its area first, so Android never hands it to a view behind.
        if (pageRows.hasFocus()) categories.get(area).requestFocus();
        pageRows.removeAllViews();
        rows.clear();
        scroller.scrollTo(0, 0);
        pageTitle.setText(NAMES[area]);
        pageNote.setText("");
        pageNote.setVisibility(View.GONE);
        for (int i = 0; i < categories.size(); i++) {
            View c = categories.get(i);
            if (!c.hasFocus()) c.setBackground(i == area ? glass(0x24FFFFFF, 0x00000000, 14) : null);
        }
        switch (area) {
            case WIFI: wifiPage(); break;
            case BLUETOOTH: bluetoothPage(); break;
            case SOUND: soundPage(); break;
            case APPS: appsPage(); break;
            case STORAGE: storagePage(); break;
            case ABOUT: aboutPage(); break;
            default: morePage(); break;
        }
    }

    private void note(String text) {
        pageNote.setText(text);
        pageNote.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---- Wi-Fi -----------------------------------------------------------------------------------

    private void wifiPage() {
        WifiManager wm = context.getApplicationContext().getSystemService(WifiManager.class);
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        Network active = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities caps = active != null ? cm.getNetworkCapabilities(active) : null;
        boolean onWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        WifiInfo info = onWifi && wm != null ? wm.getConnectionInfo() : null;
        String current = info != null && info.getSSID() != null ? info.getSSID().replaceAll("^\"|\"$", "") : "";
        if ("<unknown ssid>".equals(current)) current = "";

        section("Connected");
        if (info != null) {
            String speed = info.getLinkSpeed() > 0 ? info.getLinkSpeed() + " Mbps" : "";
            addRow(R.drawable.ic_wifi, current.isEmpty() ? "Wi-Fi" : current,
                    WheelFeeds.join(online ? "Connected" : "No internet", strength(info.getRssi()), band(info.getFrequency()), speed),
                    null, true, () -> openSystem(Settings.ACTION_WIFI_SETTINGS));
        } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            addRow(R.drawable.ic_ethernet, "Ethernet", online ? "Connected" : "No internet", null, true,
                    () -> openSystem(Settings.ACTION_WIFI_SETTINGS));
        } else {
            addRow(R.drawable.ic_wifi, "Not connected", "Pick a network below", null, false, null);
        }
        String mode = Settings.Global.getString(context.getContentResolver(), "private_dns_mode");
        String dns = Settings.Global.getString(context.getContentResolver(), "private_dns_specifier");
        boolean blocking = "hostname".equals(mode) && dns != null && !dns.isEmpty();
        addRow(blocking ? R.drawable.ic_lock : R.drawable.ic_lock_open, "Ad blocking",
                blocking ? "Private DNS: " + dns : "Off", blocking ? "On" : "Off", false, null);

        section("Other networks");
        List<ScanResult> nearby = new ArrayList<>();
        try {
            if (wm != null) nearby = wm.getScanResults();
        } catch (SecurityException ignored) {
            // no location permission: the row below says so
        }
        List<ScanResult> shown = networks(nearby, current);
        if (shown.isEmpty()) {
            addRow(R.drawable.ic_wifi, "No other networks found", "Location must be on for the TV to list them", null,
                    false, null);
        }
        for (ScanResult r : shown) {
            boolean secured = secured(r.capabilities);
            addRow(secured ? R.drawable.ic_lock : R.drawable.ic_wifi, r.SSID,
                    WheelFeeds.join(secured ? "Secured" : "Open", strength(r.level), band(r.frequency)), null, true,
                    () -> joinPage(r.SSID, r.capabilities));
        }
        addRow(R.drawable.ic_swap_vert, "Scan again", "", null, false, () -> {
            try {
                if (wm != null) wm.startScan();
                note("Scanning…");
            } catch (SecurityException ignored) {
                note("The TV did not allow a scan.");
            }
        });
        section("");
        addRow(R.drawable.ic_settings, "Advanced network settings", "Proxy, IP and saved networks, in Android's Settings",
                null, true, () -> openSystem(Settings.ACTION_WIFI_SETTINGS));
    }

    /** One row per name, strongest first, without the network the TV is on; hidden names are left out. */
    static List<ScanResult> networks(List<ScanResult> all, String current) {
        List<ScanResult> sorted = new ArrayList<>(all);
        Collections.sort(sorted, (a, b) -> Integer.compare(b.level, a.level));
        Set<String> seen = new HashSet<>();
        if (current != null && !current.isEmpty()) seen.add(current);
        List<ScanResult> out = new ArrayList<>();
        for (ScanResult r : sorted) {
            if (r.SSID == null || r.SSID.trim().isEmpty() || !seen.add(r.SSID)) continue;
            out.add(r);
            if (out.size() == 12) break;
        }
        return out;
    }

    static boolean secured(String capabilities) {
        String c = capabilities == null ? "" : capabilities;
        return c.contains("WPA") || c.contains("WEP") || c.contains("SAE") || c.contains("PSK") || c.contains("EAP");
    }

    static String strength(int rssi) {
        int level = WifiManager.calculateSignalLevel(rssi, 5);
        return level >= 4 ? "Excellent" : level == 3 ? "Good" : level == 2 ? "Fair" : "Weak";
    }

    static String band(int mhz) {
        return mhz >= 5900 ? "6 GHz" : mhz >= 4900 ? "5 GHz" : mhz > 0 ? "2.4 GHz" : "";
    }

    /**
     * Joining a network: the password is typed here, then Android is asked to save the network, which it confirms
     * once in its own small dialog and then connects to. Android 10 and later let no app join a network by itself.
     */
    private void joinPage(String ssid, String capabilities) {
        boolean secured = secured(capabilities);
        boolean enterprise = capabilities != null && capabilities.contains("EAP");
        clearPage("Join " + ssid);
        subBack = () -> showPage(WIFI);
        if (enterprise || !canSaveNetworks()) {
            // Panasonic's Settings answers the save-a-network request with its plain network page (GS655, Android
            // 11), so a password typed here would go nowhere: Android's own Wi-Fi screen joins it instead.
            note(enterprise ? "This network needs Android's own Wi-Fi screen."
                    : "On this TV, Android's Wi-Fi screen joins new networks: pick " + ssid + " there.");
            addRow(R.drawable.ic_settings, "Open Wi-Fi settings", "", null, true, () -> openSystem(Settings.ACTION_WIFI_SETTINGS));
            rows.get(0).requestFocus();
            return;
        }
        note(secured ? "Type the password, then Connect. Android asks you to confirm once." : "An open network: no password.");
        EditText password = null;
        if (secured) {
            EditText field = new EditText(context);
            field.setSingleLine(true);
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            field.setHint("Password");
            field.setHintTextColor(0x80FFFFFF);
            field.setTextColor(0xFFFFFFFF);
            field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            field.setPadding(dp(18), 0, dp(18), 0);
            field.setBackground(glass(0x29FFFFFF, 0x59FFFFFF, 22));
            field.setDefaultFocusHighlightEnabled(false);
            setFont(field, R.string.font_light);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            lp.bottomMargin = dp(10);
            pageRows.addView(field, lp);
            rows.add(field);
            wireKeys(field, null);
            password = field;
        }
        final EditText typed = password;
        addRow(R.drawable.ic_wifi, "Connect", "", null, false, () -> {
            String pass = typed == null ? "" : typed.getText().toString();
            if (typed != null && pass.length() < 8) {
                note("The password needs at least 8 characters.");
                return;
            }
            save(ssid, capabilities, pass);
        });
        rows.get(0).requestFocus();
        if (typed != null) handler.postDelayed(() -> showKeyboard(typed), 150);
    }

    /**
     * Whether Android here shows its "save this network?" prompt (Android 11 and later, AOSP's AddAppNetworks page);
     * a TV whose Settings sends the request to its general network page cannot save one for an app.
     */
    private boolean canSaveNetworks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        ResolveInfo ri = context.getPackageManager().resolveActivity(new Intent(Settings.ACTION_WIFI_ADD_NETWORKS), 0);
        String name = ri == null || ri.activityInfo == null ? "" : ri.activityInfo.name;
        return name.contains("AddAppNetworks") || name.contains("AddNetwork");
    }

    @SuppressLint("NewApi")
    private void save(String ssid, String capabilities, String pass) {
        hideKeyboard();
        WifiNetworkSuggestion.Builder b = new WifiNetworkSuggestion.Builder().setSsid(ssid);
        String c = capabilities == null ? "" : capabilities;
        if (!pass.isEmpty()) {
            if (c.contains("SAE") && !c.contains("PSK")) b.setWpa3Passphrase(pass);
            else b.setWpa2Passphrase(pass);
        }
        ArrayList<WifiNetworkSuggestion> list = new ArrayList<>();
        list.add(b.build());
        Intent add = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list);
        if (!start(add)) note("This TV can't save networks for apps. Use Advanced network settings.");
        else note("Confirm in Android's dialog. The TV then joins " + ssid + ".");
    }

    // ---- Bluetooth -------------------------------------------------------------------------------

    private void bluetoothPage() {
        BluetoothManager bm = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        if (adapter == null) {
            addRow(R.drawable.ic_bluetooth, "Bluetooth", "This TV has no Bluetooth", null, false, null);
            return;
        }
        boolean on = adapter.isEnabled();
        int state = adapter.getState();
        String now = state == BluetoothAdapter.STATE_TURNING_ON ? "Turning on…"
                : state == BluetoothAdapter.STATE_TURNING_OFF ? "Turning off…" : on ? "On" : "Off";
        addRow(R.drawable.ic_bluetooth, "Bluetooth", "OK switches it " + (on ? "off" : "on"), now, false, () -> {
            boolean done;
            try {
                done = on ? adapter.disable() : adapter.enable();
            } catch (SecurityException e) {
                done = false;
            }
            note(done ? (on ? "Turning off…" : "Turning on…") : "The TV did not allow that. Use Android's Bluetooth settings.");
        });
        if (on) {
            Set<String> connected = new HashSet<>();
            try {
                for (BluetoothDevice d : bm.getConnectedDevices(BluetoothProfile.GATT)) connected.add(d.getAddress());
            } catch (Exception ignored) {
                // no GATT list: shown as paired
            }
            connected.addAll(audioConnected);
            section("Paired devices");
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) {
                addRow(R.drawable.ic_bluetooth, "No paired devices", "", null, false, null);
            } else {
                List<BluetoothDevice> list = new ArrayList<>(bonded);
                Collections.sort(list, (a, b) -> {
                    boolean ca = connected.contains(a.getAddress()), cb = connected.contains(b.getAddress());
                    if (ca != cb) return ca ? -1 : 1;
                    return String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName()));
                });
                for (BluetoothDevice d : list) {
                    String name = d.getName() == null || d.getName().isEmpty() ? d.getAddress() : d.getName();
                    boolean isOn = connected.contains(d.getAddress());
                    addRow(R.drawable.ic_bluetooth, name, WheelFeeds.join(isOn ? "Connected" : "Paired", kind(d)),
                            null, true, () -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS));
                }
            }
            loadAudioConnections(adapter);
        }
        section("");
        addRow(R.drawable.ic_settings, "Pair a new device", "Remote, speaker or headphones, in Android's Settings",
                null, true, () -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    /** Speakers and headphones that are connected, from the audio profile (asked for once per page). */
    private final Set<String> audioConnected = new HashSet<>();
    private boolean audioAsked;

    private void loadAudioConnections(BluetoothAdapter adapter) {
        if (audioAsked) return;
        audioAsked = true;
        try {
            adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    boolean changed = false;
                    for (BluetoothDevice d : proxy.getConnectedDevices()) changed |= audioConnected.add(d.getAddress());
                    adapter.closeProfileProxy(profile, proxy);
                    if (changed) handler.post(() -> {
                        if (category == BLUETOOTH) refresh();
                    });
                }

                @Override
                public void onServiceDisconnected(int profile) {
                }
            }, BluetoothProfile.A2DP);
        } catch (Exception ignored) {
            // no audio profile on this TV
        }
    }

    private static String kind(BluetoothDevice d) {
        BluetoothClass c = d.getBluetoothClass();
        if (c == null) return "";
        switch (c.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "Audio";
            case BluetoothClass.Device.Major.PERIPHERAL: return "Remote or keyboard";
            case BluetoothClass.Device.Major.PHONE: return "Phone";
            case BluetoothClass.Device.Major.COMPUTER: return "Computer";
            default: return "";
        }
    }

    // ---- Sound -----------------------------------------------------------------------------------

    private void soundPage() {
        AudioManager am = context.getSystemService(AudioManager.class);
        if (am == null) return;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        addSlider(R.drawable.ic_volume, "Volume", () -> am.getStreamVolume(AudioManager.STREAM_MUSIC), max, v ->
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0));
        boolean muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
        addRow(R.drawable.ic_volume, "Mute", "OK switches it", muted ? "On" : "Off", false, () -> {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
            refresh();
        });
        section("");
        addRow(R.drawable.ic_settings, "Sound output and audio settings", "Speakers, sound modes and more, in Android's Settings",
                null, true, () -> openSystem(Settings.ACTION_SOUND_SETTINGS));
    }

    // ---- Apps ------------------------------------------------------------------------------------

    private void appsPage() {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<ResolveInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null || context.getPackageName().equals(ri.activityInfo.packageName)) continue;
            if (seen.add(ri.activityInfo.packageName)) apps.add(ri);
        }
        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        note(apps.size() + " apps");
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            String label = String.valueOf(ri.loadLabel(pm));
            String version = "";
            try {
                PackageInfo p = pm.getPackageInfo(pkg, 0);
                version = p.versionName == null ? "" : "Version " + p.versionName;
            } catch (PackageManager.NameNotFoundException ignored) {
                // gone meanwhile
            }
            String size = sizeOf(new File(ri.activityInfo.applicationInfo.sourceDir).length());
            View row = addRow(0, label, WheelFeeds.join(version, size), null, true, () -> appPage(ri, label));
            row.setTag(pkg);   // Back from its page finds it again
            ImageView icon = row.findViewWithTag("icon");
            if (icon != null) {
                icon.setImageDrawable(ri.loadIcon(pm));
                icon.clearColorFilter();
                icon.setTag("keep-colour");
            }
        }
    }

    private void appPage(ResolveInfo ri, String label) {
        String pkg = ri.activityInfo.packageName;
        clearPage(label);
        subBack = () -> {
            showPage(APPS);
            for (int i = 0; i < rows.size(); i++) {
                Object t = rows.get(i).getTag();
                if (pkg.equals(t)) rows.get(i).requestFocus();
            }
        };
        boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        note(system ? "Part of the TV's software: it can be stopped in App info, not uninstalled." : "");
        addRow(R.drawable.ic_apps, "Open", "", null, false, () -> {
            Intent i = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i != null) start(i);
        });
        addRow(R.drawable.ic_info, "App info", "Storage, permissions, force stop, in Android's Settings", null, true,
                () -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))));
        if (!system) {
            addRow(R.drawable.ic_lock_open, "Uninstall", "Android asks you to confirm", null, false,
                    () -> start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))));
        }
        rows.get(0).requestFocus();
    }

    // ---- Storage ---------------------------------------------------------------------------------

    private void storagePage() {
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long total = fs.getTotalBytes(), free = fs.getAvailableBytes(), used = total - free;
        View row = addRow(R.drawable.ic_storage, "Internal storage", sizeOf(used) + " used of " + sizeOf(total),
                sizeOf(free) + " free", false, null);
        Bar bar = new Bar(context, total > 0 ? used / (float) total : 0f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        lp.setMargins(dp(14), dp(2), dp(14), dp(14));
        pageRows.addView(bar, pageRows.indexOfChild(row) + 1, lp);
        section("");
        addRow(R.drawable.ic_settings, "Manage storage", "Clear caches and see what uses the space, in Android's Settings",
                null, true, () -> openSystem(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
    }

    // ---- About -----------------------------------------------------------------------------------

    private void aboutPage() {
        String model = (Build.MANUFACTURER == null ? "" : capitalised(Build.MANUFACTURER) + " ") + Build.MODEL;
        addRow(R.drawable.ic_info, "Model", "", model, false, null);
        addRow(R.drawable.ic_info, "Android TV", "", Build.VERSION.RELEASE, false, null);
        addRow(R.drawable.ic_info, "Security update", "", Build.VERSION.SECURITY_PATCH, false, null);
        addRow(R.drawable.ic_info, "Software build", "", Build.DISPLAY, false, null);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        ActivityManager am = context.getSystemService(ActivityManager.class);
        if (am != null) {
            am.getMemoryInfo(mem);
            addRow(R.drawable.ic_info, "Memory", "", sizeOf(mem.totalMem) + "  ·  " + sizeOf(mem.availMem) + " free", false, null);
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        addRow(R.drawable.ic_info, "Screen", "", dm.widthPixels + " × " + dm.heightPixels, false, null);
        String launcher = "";
        try {
            launcher = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // always there
        }
        addRow(R.drawable.ic_home_settings, "Home", "", launcher, false, null);
        long up = SystemClock.elapsedRealtime() / 60000;
        addRow(R.drawable.ic_info, "On for", "", up >= 60 ? up / 60 + " h " + up % 60 + " min" : up + " min", false, null);
        section("");
        addRow(R.drawable.ic_settings, "System update", "Check for a Panasonic update, in Android's Settings", null, true,
                () -> openSystem(Settings.ACTION_DEVICE_INFO_SETTINGS));
    }

    /**
     * Android's Date and time page. Two pages answer the request on this TV, and Panasonic's Timer page (sleep and
     * on/off timers, partnercustomizer.timer) wins over Android's own system.DateTimeActivity: name Android's directly.
     */
    private Intent dateTimeIntent() {
        Intent any = new Intent(Settings.ACTION_DATE_SETTINGS);
        for (ResolveInfo ri : context.getPackageManager().queryIntentActivities(any, 0)) {
            if (ri.activityInfo != null && ri.activityInfo.name.endsWith("DateTimeActivity")) {
                return new Intent(any).setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
            }
        }
        return any;
    }

    private static String capitalised(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    // ---- More settings: what only Android's Settings can change ----------------------------------

    private void morePage() {
        note("These pages belong to Android's Settings and open there.");
        addRow(R.drawable.ic_picture, "Display and picture", "Picture mode, brightness, resolution", null, true,
                () -> start(SystemSettings.displayIntent(context)));
        addRow(R.drawable.ic_settings, "Date and time", "Automatic time, date, time zone, 24-hour clock", null, true,
                () -> start(dateTimeIntent()));
        addRow(R.drawable.ic_info, "Accessibility", "", null, true, () -> openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        addRow(R.drawable.ic_lock, "Location", "", null, true, () -> openSystem(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        addRow(R.drawable.ic_settings, "All Android settings", "", null, true, () -> openSystem(Settings.ACTION_SETTINGS));
        section("");
        addRow(R.drawable.ic_home_settings, "Launcher settings", "Wallpaper, dock and Home's own options", null, true, () -> {
            closeNow();
            host.openLauncherSettings();
        });
    }

    // ---- rows ------------------------------------------------------------------------------------

    private void clearPage(String title) {
        hideKeyboard();
        if (pageRows.hasFocus()) categories.get(category).requestFocus();
        pageRows.removeAllViews();
        rows.clear();
        scroller.scrollTo(0, 0);
        pageTitle.setText(title);
        note("");
    }

    private void section(String name) {
        TextView label = text(11, 0x8CFFFFFF, R.string.font_medium);
        label.setText(name.toUpperCase(Locale.getDefault()));
        label.setLetterSpacing(0.16f);
        label.setPadding(dp(14), dp(pageRows.getChildCount() == 0 ? 2 : 16), 0, dp(6));
        if (name.isEmpty()) label.setPadding(0, dp(8), 0, dp(4));
        pageRows.addView(label);
    }

    /**
     * A row of the Inputs kind: icon, title, a line under it, a value at the right, and a chevron when it opens one of
     * Android's pages. Focused it turns white with dark ink. Rows without an action still take focus, to be read.
     */
    private View addRow(int iconRes, String title, String sub, String value, boolean chevron, Runnable ok) {
        LinearLayout row = row();
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(48));
        row.setFocusable(true);
        row.setDefaultFocusHighlightEnabled(false);
        ImageView icon = new ImageView(context);
        icon.setTag("icon");
        if (iconRes != 0) icon.setImageResource(iconRes);
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout words = column();
        words.setPadding(dp(14), 0, dp(10), 0);
        TextView name = text(16, 0xFFFFFFFF, R.string.font_medium);
        name.setText(title);
        words.addView(name);
        TextView line = text(12, 0x8CFFFFFF, R.string.font_regular);
        line.setText(sub == null ? "" : sub);
        line.setPadding(0, dp(3), 0, 0);
        line.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        words.addView(line);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = text(14, 0xB3FFFFFF, R.string.font_regular);
        right.setText(value == null ? "" : value);
        right.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
        row.addView(right);
        TextView arrow = text(20, 0x80FFFFFF, R.string.font_light);
        arrow.setText("›");
        arrow.setPadding(dp(10), 0, 0, 0);
        arrow.setVisibility(chevron ? View.VISIBLE : View.GONE);
        row.addView(arrow);
        GradientDrawable focus = glass(0xF5FFFFFF, 0xF5FFFFFF, 14);
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? focus : null);
            name.setTextColor(has ? 0xFF111111 : 0xFFFFFFFF);
            line.setTextColor(has ? 0xCC111111 : 0x8CFFFFFF);
            right.setTextColor(has ? 0xFF111111 : 0xB3FFFFFF);
            arrow.setTextColor(has ? 0xFF111111 : 0x80FFFFFF);
            if (!"keep-colour".equals(icon.getTag())) icon.setColorFilter(has ? 0xFF111111 : 0xFFFFFFFF);
            if (has) v.post(() -> scroller.smoothScrollTo(0, Math.max(0, v.getTop() - dp(40))));
        });
        icon.setColorFilter(0xFFFFFFFF);
        pageRows.addView(row);
        rows.add(row);
        wireKeys(row, ok);
        return row;
    }

    /** A volume-like row: Left and Right step it, the bar under the title shows where it is. */
    private interface Level {
        int get();
    }

    private interface Setter {
        void set(int value);
    }

    private void addSlider(int iconRes, String title, Level level, int max, Setter setter) {
        View row = addRow(iconRes, title, "‹  Left and Right change it  ›", String.valueOf(level.get()), false, null);
        Bar bar = new Bar(context, max > 0 ? level.get() / (float) max : 0f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        lp.setMargins(dp(50), dp(2), dp(14), dp(12));
        pageRows.addView(bar, pageRows.indexOfChild(row) + 1, lp);
        TextView value = (TextView) ((ViewGroup) row).getChildAt(2);
        row.setOnKeyListener((v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int to = Math.max(0, Math.min(max, level.get() + (key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)));
                setter.set(to);
                value.setText(String.valueOf(level.get()));
                bar.set(max > 0 ? level.get() / (float) max : 0f);
                return true;
            }
            return rowKey(rows.indexOf(v), key, e, null);
        });
    }

    private void wireKeys(View row, Runnable ok) {
        row.setOnKeyListener((v, key, e) -> e.getAction() == KeyEvent.ACTION_DOWN && rowKey(rows.indexOf(v), key, e, ok));
    }

    private boolean rowKey(int index, int key, KeyEvent e, Runnable ok) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (index > 0) rows.get(index - 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (index >= 0 && index < rows.size() - 1) rows.get(index + 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (rows.get(Math.max(0, index)) instanceof EditText) return false;   // the cursor in the password
                back();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return !(index >= 0 && rows.get(index) instanceof EditText);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (index >= 0 && rows.get(index) instanceof EditText) return false;   // opens the keyboard
                if (e.getRepeatCount() == 0 && ok != null) ok.run();
                return true;
            case KeyEvent.KEYCODE_BACK:
                back();
                return true;
            default:
                return false;
        }
    }

    /** One step out: from an app's or a network's page to its list, from a page to the areas. */
    private void back() {
        hideKeyboard();
        if (subBack != null) {
            Runnable r = subBack;
            subBack = null;
            r.run();
            return;
        }
        categories.get(category).requestFocus();
    }

    // ---- bits ------------------------------------------------------------------------------------

    private void openSystem(String action) {
        start(new Intent(action));
    }

    private boolean start(Intent intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            note("This TV has no screen for that.");
            return false;
        }
    }

    static String sizeOf(long bytes) {
        if (bytes <= 0) return "";
        double gb = bytes / (1024.0 * 1024 * 1024);
        if (gb >= 1) return String.format(Locale.getDefault(), gb >= 10 ? "%.0f GB" : "%.1f GB", gb);
        double mb = bytes / (1024.0 * 1024);
        return String.format(Locale.getDefault(), mb >= 10 ? "%.0f MB" : "%.1f MB", mb);
    }

    /** A thin track filled to a share: storage used, the volume. */
    private static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float share;

        Bar(Context c, float share) {
            super(c);
            this.share = share;
            track.setColor(0x33FFFFFF);
            fill.setColor(0xFFFFFFFF);
        }

        void set(float share) {
            this.share = share;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth();
            r.set(0, 0, w, h);
            canvas.drawRoundRect(r, h / 2, h / 2, track);
            r.set(0, 0, Math.max(h, w * Math.max(0f, Math.min(1f, share))), h);
            canvas.drawRoundRect(r, h / 2, h / 2, fill);
        }
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private GradientDrawable glass(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(Math.max(1, dp(1)), stroke);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(float sp, int color, int font) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        setFont(t, font);
        return t;
    }

    private void setFont(TextView t, int font) {
        try {
            t.setTypeface(UiFont.get(context, font));
        } catch (Exception ignored) {
            // the system font is fine
        }
    }

    private void showKeyboard(View field) {
        if (!opened || !field.hasFocus()) return;
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(layer.getWindowToken(), 0);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
