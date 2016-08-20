package com.example.bluetoothgattperipheral;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import android.R.integer;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

/**
 * Dave Smith
 * Date: 11/13/14
 * ClientActivity
 */
@SuppressLint("NewApi")
public class ClientActivity extends Activity {
    private static final String TAG = "gomtel";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private SparseArray<BluetoothDevice> mDevices;

    private BluetoothGatt mBluetoothGatt;

    private Handler mHandler = new Handler(){
    	
    };

    /* 客户端UI元素 */
    private TextView mLatestValue;
    private TextView mCurrentOffset;
    private EditText mEditText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        mLatestValue = (TextView) findViewById(R.id.latest_value);
        mCurrentOffset = (TextView) findViewById(R.id.offset_date);
        mEditText = (EditText)findViewById(R.id.et);
        updateDateText(0);

        /*
         * 蓝牙技术在Android 4.3 +是通过BluetoothManager访问，
         * 而不是旧的静态 BluetoothAdapter.getInstance()
         */
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        mDevices = new SparseArray<BluetoothDevice>();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * We need to enforce that Bluetooth is first enabled, and take the
         * user to settings to enable it if they have not done so.
         * 我们第一次需要强制执行蓝牙启用，如果他们没有这样做，则用户将设置为启用。
         */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //蓝牙是禁用的
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        /*
         * Check for Bluetooth LE Support.  In production, our manifest entry will keep this
         * from installing on these devices, but this will allow test devices or other
         * sideloads to report whether or not the feature exists.
         * 检查蓝牙BLE。在产品中，我们的清单条目将保持这个从安装在这些设备上，
         * 但这将允许测试设备或其他侧向载荷，报告是否存在的特征。
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "不支持BLE.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        //停止任何活动扫描
        stopScan();
        //断开任何活动连接
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan, menu);
        //Add any device elements we've discovered to the overflow menu
        //添加发现的已填充菜单的任何设备元素
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                mDevices.clear();
                startScan();
                return true;
            default:
                //获取所发现的设备连接
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to " + device.getName());
                /*
                 * Make a connection with the device using the special LE-specific
                 * connectGatt() method, passing in a callback for GATT events
                 * 使用connectGatt()方法在指定的BLE设备与设备的连接，通过在GATT事件回调
                 */
                mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
                return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Select a new time to set as the base offset
     * on the GATT Server. Then write to the characteristic.
     * 选择要设置为偏移对GATT服务的基础上建立一个新的时间。然后写入 characteristic
     */
    public void onUpdateClick(View v) {
        if (mBluetoothGatt != null) {
            final Calendar now = Calendar.getInstance();
            TimePickerDialog dialog = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    now.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    now.set(Calendar.MINUTE, minute);
                    now.set(Calendar.SECOND, 0);
                    now.set(Calendar.MILLISECOND, 0);

                    BluetoothGattCharacteristic characteristic = mBluetoothGatt
                            .getService(DeviceProfile.SERVICE_UUID)
                            .getCharacteristic(DeviceProfile.CHARACTERISTIC_OFFSET_UUID);
                    
                    byte[] value = DeviceProfile.bytesFromInt((int)(now.getTimeInMillis()/1000));
                    Log.i(TAG, "正在写入值的大小： " + value.length + ",value = " + value.toString());
                    characteristic.setValue(value);

                    mBluetoothGatt.writeCharacteristic(characteristic);
                }
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
            dialog.show();
        }
    }

    /*
     * 检索时间偏移的当前值
     */
    public void onGetOffsetClick(View v) {
        if (mBluetoothGatt != null) {
            BluetoothGattCharacteristic characteristic = mBluetoothGatt
                    .getService(DeviceProfile.SERVICE_UUID)
                    .getCharacteristic(DeviceProfile.CHARACTERISTIC_OFFSET_UUID);

            mBluetoothGatt.readCharacteristic(characteristic);
            mCurrentOffset.setText("---");
        }
    }
    
    int i = 0;
    
    /**
     * 发送字符串
     */
    public void onSendEdit(View v) {

		/*setCharacteristicNotification(DeviceProfile.SERVICE_UUID, DeviceProfile.CHARACTERISTIC_DEMO_UUID, true);
		readCharacteristic(DeviceProfile.SERVICE_UUID, DeviceProfile.CHARACTERISTIC_DEMO_UUID);	*/
		
    	if (mBluetoothGatt != null) {
    		final BluetoothGattCharacteristic characteristic = mBluetoothGatt
                    .getService(DeviceProfile.SERVICE_UUID)
                    .getCharacteristic(DeviceProfile.CHARACTERISTIC_DEMO_UUID);
    		byte[] value = DeviceProfile.bytesFromInt((int)(System.currentTimeMillis()/1000));
            Log.i(TAG, "Writing value of size " + value.length);
            i++;
            
            mHandler.post(new Runnable() {
				public void run() {
					byte[] icon = DeviceProfile.getIcon(getApplicationContext());
					//characteristic.setValue(mEditText.getText().toString() + i);
					characteristic.setValue(icon);
					
					mBluetoothGatt.writeCharacteristic(characteristic);
					mHandler.sendEmptyMessage(11);
				}
			});
            
    		//writeCharacteristic(DeviceProfile.SERVICE_UUID, DeviceProfile.CHARACTERISTIC_OFFSET_UUID, "1");
    	}
    }

    private void updateDateText(long offset) {
        Date date = new Date(offset);
        String dateString = DateFormat.getDateTimeInstance().format(date);
        mCurrentOffset.setText(dateString);
    }

    /*
     * Begin a scan for new servers that advertise our
     * matching service.
     * 开始扫描新的服务，为我们的匹配服务做advertise
     */
	private void startScan() {
		//扫描我们的自定义的设备服务advertising
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(DeviceProfile.SERVICE_UUID))
                .build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, mScanCallback);
    }

    /*
     * 终止任何活动扫描
     */
	private void stopScan() {
		if (mScanCallback != null && mBluetoothAdapter.getBluetoothLeScanner() != null)
			mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
	}

    /*
     * Callback handles results from new devices that appear
     * during a scan. Batch results appear when scan delay
     * filters are enabled.
     * 回调和处理在扫描过程中出现的新设备的结果。当启用扫描延迟筛选器时，批处理结果会出现。
     */
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG, "onScanResult");
            processResult(result);
        }

        @Override
		public void onBatchScanResults(List<ScanResult> results) {
			Log.i(TAG, "onBatchScanResults: " + results.size() + " results");

            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "LE 扫描失败: "+errorCode);
        }

        private void processResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            Log.i(TAG, "New LE Device: " + device.getName() + " @ " + result.getRssi());
            //将它添加到集合中
            mDevices.put(device.hashCode(), device);
            //更新填充的菜单
            invalidateOptionsMenu();

            stopScan();
        }
    };

    /*
     * Callback handles GATT client events, such as results from
     * reading or writing a characteristic value on the server.
     * 
     * GATT回调处理客户端事件，如果需要读取或写入的特征值对服务器的结果
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.i(TAG, "onConnectionStateChange "
                    +DeviceProfile.getStatusDescription(status)+" "
                    +DeviceProfile.getStateDescription(newState));

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(TAG, "onServicesDiscovered:");

            for (BluetoothGattService service : gatt.getServices()) {
                Log.i(TAG, "Service: "+service.getUuid());

                if (DeviceProfile.SERVICE_UUID.equals(service.getUuid())) {
                    //读取当前 characteristic 的值
                    gatt.readCharacteristic(service.getCharacteristic(DeviceProfile.CHARACTERISTIC_ELAPSED_UUID));
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            final int charValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);

            if (DeviceProfile.CHARACTERISTIC_ELAPSED_UUID.equals(characteristic.getUuid())) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mLatestValue.setText(String.valueOf(charValue));
                    }
                });

                //注册更多更新作为通知
                gatt.setCharacteristicNotification(characteristic, true);
            }

            if (DeviceProfile.CHARACTERISTIC_OFFSET_UUID.equals(characteristic.getUuid())) {
				Log.i(TAG, "Current time offset: " + charValue);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateDateText((long)charValue * 1000);
                    }
                });
            }
        }

		@Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(TAG, "Notification of time characteristic changed on server.");
            final int charValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mLatestValue.setText(String.valueOf(charValue));
                }
            });
        }
    };
    

    public void readCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        if (mBluetoothGatt != null) {
            BluetoothGattService service =
            		mBluetoothGatt.getService(serviceUUID);
            BluetoothGattCharacteristic characteristic =
                    service.getCharacteristic(characteristicUUID);
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    public void setCharacteristicNotification(UUID serviceUUID, UUID characteristicUUID,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
		}
		BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
		if(characteristic == null){
            Log.w(TAG, "characteristic not initialized");
            return;
		}

        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

	public void writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, String value) {
		if (mBluetoothGatt == null) {
			Log.i("gomtel", "mBluetoothGatt not initialized");
			return;
		}
		BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
		if (service == null) {
			Log.w(TAG, "service not initialized");
			return;
		}
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
		if (characteristic == null) {
			Log.w(TAG, "characteristic not initialized");
			return;
		}

		characteristic.setValue(value);
		mBluetoothGatt.writeCharacteristic(characteristic);
	}

}
