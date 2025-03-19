package net.mhu.home.blat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import net.mhu.home.blat.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements ConnectFailedFragment.NoticeDialogListener {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mScanner;
    private boolean mScanning;
    private Handler mHandler;
    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private boolean mConnected = false;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;

    private static final String TAG = "BLEVoltageCurrent";
     private ActivityMainBinding binding;
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                if(BluetoothLeService.BLE_CHAR_BATTERY.equals((UUID)intent.getSerializableExtra("uuid")) ) {
                    BatteryData data = (BatteryData) intent.getSerializableExtra(BluetoothLeService.EXTRA_DATA);
                    renderBatteryData(data);
                }
            }
        }
    };
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);

        return intentFilter;
    }
    // Define the request code for Bluetooth permissions
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private ActivityResultContracts.RequestMultiplePermissions multiplePermissionsContract;
    private ActivityResultLauncher<String[]> multiplePermissionLauncher;

    final String[] PERMISSIONS = {
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT"
    };


    private void askPermissions(ActivityResultLauncher<String[]> multiplePermissionLauncher) {
        if (!hasPermissions(PERMISSIONS)) {
            Log.d("PERMISSIONS", "Launching multiple contract permission launcher for ALL required permissions");
            multiplePermissionLauncher.launch(PERMISSIONS);
        } else {
            Log.d("PERMISSIONS", "All permissions are already granted");
            scanLeDevice(true);

        }
    }

    private boolean hasPermissions(String[] permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("PERMISSIONS", "Permission is not granted: " + permission);
                    return false;
                }
                Log.d("PERMISSIONS", "Permission already granted: " + permission);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.numbers_and_such);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter(), RECEIVER_NOT_EXPORTED);

        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        //request bluetooth permissions
        multiplePermissionsContract = new ActivityResultContracts.RequestMultiplePermissions();
        multiplePermissionLauncher = registerForActivityResult(multiplePermissionsContract, isGranted -> {
            Log.d("PERMISSIONS", "Launcher result: " + isGranted.toString());
            if (isGranted.containsValue(false)) {
                Log.d("PERMISSIONS", "At least one of the permissions was not granted, launching again...");
                multiplePermissionLauncher.launch(PERMISSIONS);
            }
            else {
                scanLeDevice(true);

            }
        });
        mScanner = mBluetoothAdapter.getBluetoothLeScanner();
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        askPermissions(multiplePermissionLauncher);



    }

    public void showNoticeDialog() {
        // Create an instance of the dialog fragment and show it.
        DialogFragment dialog = new ConnectFailedFragment();
        dialog.show(getSupportFragmentManager(), "NoticeDialogFragment");
    }



    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        // User taps the dialog's positive button.
        scanLeDevice(true);
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        // User taps the dialog's negative button.
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanLeDevice(true);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanLeDevice(false);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    // Device scan callback.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice device = result.getDevice();
                    mDeviceAddress = device.getAddress();
                    mDeviceName = device.getName();

                    mBluetoothLeService.connect(mDeviceAddress);
                    scanLeDevice(false);
                }
            };

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mScanner.stopScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
            scanFilterBuilder.setDeviceName("BluetoothBattery");
            filters.add(scanFilterBuilder.build());

            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
            settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setNumOfMatches(1);
            mScanner.startScan(filters, settingsBuilder.build(), mLeScanCallback);
        } else {
            mScanning = false;
            mScanner.stopScan(mLeScanCallback);
        }
    }

    private void renderBatteryData(BatteryData data) {
        TextView textCapacity = (TextView) findViewById(R.id.textCapacity);
        textCapacity.setText(MessageFormat.format("{0,number,integer} %", (int) data.getCapacity()));

        TextView textCapacityWh = (TextView) findViewById(R.id.textCapacityWh);
        textCapacityWh.setText(MessageFormat.format("{0,number,integer} Wh", (int) data.getCapacityWh()));

        TextView textVoltage = (TextView) findViewById(R.id.textVoltage);
        textVoltage.setText(MessageFormat.format("{0,number,#.#} V", data.getVoltage()));

        TextView textPower = (TextView) findViewById(R.id.textPower);
        textPower.setText(MessageFormat.format("{0,number,#.#} W", data.getPower()));

        TextView textPower1h = (TextView) findViewById(R.id.textPower1h);
        textPower1h.setText(MessageFormat.format("{0,number,integer} Wh", data.getPowerLast1h()));

        TextView textPower24h = (TextView) findViewById(R.id.textPower24h);
        textPower24h.setText(MessageFormat.format("{0,number,integer} Wh", data.getPowerLast24h()));

        TextView labelTimeTo = (TextView) findViewById(R.id.labelTimeTo);
        TextView textTimeTo = (TextView) findViewById(R.id.textTimeTo);
        if( data.getMinutesToEmpty() == -1) {
            labelTimeTo.setText("Time to full (based on last 1h)");
            textTimeTo.setText(formatDuration(data.getMinutesToFull()));
        }
        else {
            labelTimeTo.setText("Time to empty (based on last 1h)");
            textTimeTo.setText(formatDuration(data.getMinutesToEmpty()));
        }
    }

    private static String formatDuration(int minutes) {

        if(minutes >= 10*24*60) {
            return "many days";
        }
        if( minutes < 60) {
            return MessageFormat.format("{0,number,integer} min", minutes);
        }
        if( minutes < 60*24) {
            return MessageFormat.format("{0,number,integer}h {1,number,integer}m", minutes / 60, minutes % 60);
        }
        return MessageFormat.format("{0,number,integer}d {1,number,integer}h {2,number,integer}m", minutes /(24*60), minutes % (24*60), (minutes % (24*60)) % 60);

    }


}