package net.mhu.home.blat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import net.mhu.home.blat.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLEVoltageCurrent";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private static final long RECONNECT_DELAY = 5000; // 5 seconds

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler handler;

    private static final String DEVICE_ADDRESS = "YOUR_DEVICE_ADDRESS"; //Replace with your device address
    private static final UUID SERVICE_UUID = UUID.fromString("YOUR_SERVICE_UUID"); //Replace with your service UUID
    private static final UUID VOLTAGE_CHARACTERISTIC_UUID = UUID.fromString("YOUR_VOLTAGE_CHARACTERISTIC_UUID"); //Replace with your voltage characteristic UUID

    private boolean isScanning = false;
    private boolean isConnected = false;
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        handler = new Handler();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            checkPermissionsAndStartScanning();
        }
    }

    private void checkPermissionsAndStartScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_LOCATION_PERMISSION);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
                return;
            }
        }
        startScanning();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                showRetryDialog();
            }
        }
    }

    private void startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            return;
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        List<ScanFilter> filters = new ArrayList<>();
        // You can add filters if needed, for example to filter by service UUID

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothLeScanner.startScan(filters, settings, scanCallback);

        isScanning = true;
        handler.postDelayed(() -> {
            if (isScanning) {
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                if (!isConnected) {
                    showRetryDialog();
                }
            }
        }, SCAN_PERIOD);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device.getAddress().equals(DEVICE_ADDRESS)) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                connectToDevice(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error: " + errorCode);
            showRetryDialog();
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
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
            labelTimeTo.setText("Time to full (based on lst 1h)");
            textTimeTo.setText(formatDuration(data.getMinutesToFull()));
        }
        else {
            labelTimeTo.setText("Time to empty (based on lst 1h)");
            textTimeTo.setText(formatDuration(data.getMinutesToEmpty()));
        }
    }

    private String formatDuration(int minutes) {

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
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}