package com.example.bluetoothgattperipheral;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import com.ab.util.AmazingToast;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;

/**
 * Dave Smith
 * Date: 11/13/14
 * DeviceProfile
 * Service/Characteristic constant for our custom peripheral
 */
public class DeviceProfile {

    /* Unique ids generated for this device by 'uuidgen'. Doesn't conform to any SIG profile. */

    //Service UUID to expose our time characteristics
    public static UUID SERVICE_UUID = UUID.fromString("1706BBC0-88AB-4B8D-877E-2237916EE929");
    //Read-only characteristic providing number of elapsed seconds since offset
    public static UUID CHARACTERISTIC_ELAPSED_UUID = UUID.fromString("275348FB-C14D-4FD5-B434-7C3F351DEA5F");
    //Read-write characteristic for current offset timestamp
    public static UUID CHARACTERISTIC_OFFSET_UUID = UUID.fromString("BD28E457-4026-4270-A99F-F9BC20182E15");
    //Read-write characteristic for current offset timestamp
    public static UUID CHARACTERISTIC_DEMO_UUID = UUID.fromString("BD28E457-4026-4270-A99F-F9BC20182E15");

    public static String getStateDescription(int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "Connected";
            case BluetoothProfile.STATE_CONNECTING:
                return "Connecting";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "Disconnected";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "Disconnecting";
            default:
                return "Unknown State "+state;
        }
    }

    public static String getStatusDescription(int status) {
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "SUCCESS";
            default:
                return "Unknown Status "+status;
        }
    }

    public static byte[] getShiftedTimeValue(int timeOffset) {
        int value = Math.max(0,
                (int)(System.currentTimeMillis()/1000) - timeOffset);
        return bytesFromInt(value);
    }

    public static int unsignedIntFromBytes(byte[] raw) {
        if (raw.length < 4) throw new IllegalArgumentException("Cannot convert raw data to int");

        return ((raw[0] & 0xFF)
                + ((raw[1] & 0xFF) << 8)
                + ((raw[2] & 0xFF) << 16)
                + ((raw[3] & 0xFF) << 24));
    }

    public static byte[] bytesFromInt(int value) {
        //Convert result into raw bytes. GATT APIs expect LE order
        return ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array();
    }

    /**
     * 通过包名获取应用程序的图标
     * @param pContext
     * @param pk
     * @return 返回包名所对应的应用程序的Icon
     */
    public static byte[] getProgramIconByPackageName(Context pContext, String pk) {
        try {
            //使用Bitmap加Matrix来缩放
            Bitmap _Bitmap = ((BitmapDrawable) pContext.getPackageManager().getApplicationIcon(pk))
                    .getBitmap();
            int width = _Bitmap.getWidth();
            int height = _Bitmap.getHeight();
            float maxWidthOrHeight = 70f;
            float scale = 1f;
            if (width >= height && width >= maxWidthOrHeight) {             // 如果宽度大的话根据宽度固定大小缩放
                scale = (maxWidthOrHeight / width);
            } else if (width < height && height > maxWidthOrHeight)         // 如果高度高的话根据宽度固定大小缩放
                scale = (maxWidthOrHeight / height);

            scale = scale >= 1 ? 1 : scale;

            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            Bitmap resizedBitmap = Bitmap.createBitmap(_Bitmap, 0, 0, width, height, matrix, true);
            return Bitmap2Bytes(resizedBitmap);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
	}

	public static byte[] getIcon(Context pContext) {
		Resources res = pContext.getResources();
		Bitmap bmp = BitmapFactory.decodeResource(res, R.drawable.ic_launcher);
		AmazingToast.showToast(pContext, "size = " + getBitmapsize(bmp), 1);
		return Bitmap2Bytes(bmp);
	}

	public static byte[] Bitmap2Bytes(Bitmap bm) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
		return baos.toByteArray();
	}

	public static long getBitmapsize(Bitmap bitmap) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1)
			return bitmap.getByteCount();
		// Pre HC-MR1
		return bitmap.getRowBytes() * bitmap.getHeight();
	}
}
