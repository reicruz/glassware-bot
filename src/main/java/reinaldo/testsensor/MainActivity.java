package reinaldo.testsensor;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements SensorEventListener {

    SensorManager mSensorManager;
    Sensor mAccel;
    Sensor mGrav;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mDevice;
    ConnectedThread mConnectedThread;
    ConnectThread mConnectThread;
    String writeMessage = "";
    Bitmap bMap;

    boolean connected;
    boolean compassReceived;
    boolean firstTime;
    boolean picComing;
    boolean picSizeRcvd;
    boolean picHere;
    boolean startPic;

    int picSize;

    float[] mGravity;
    float[] mGeomagnetic;

    float carCompass;
    float azimuth;
    float x, y;

    TextView mTextView;
    ImageView mImageView;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        connected = false;
        compassReceived = false;
        firstTime = true;
        picComing = false;
        picSizeRcvd = false;
        picHere = false;
        startPic = false;

        setContentView(R.layout.activity_main);
        mTextView = (TextView) findViewById(R.id.text);
        mImageView = (ImageView) findViewById(R.id.image);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGrav = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGrav, SensorManager.SENSOR_DELAY_NORMAL);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.d("BLUETOOTH", "Not Connected");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.d("BLUETOOTH", "Paired: " + device.getName());
                mDevice = device;
            }
        }

        mConnectThread = new ConnectThread(mDevice);
        mConnectThread.start();



    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, mAccel);
        mSensorManager.unregisterListener(this, mGrav);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGrav, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGrav, SensorManager.SENSOR_DELAY_NORMAL);
        if (mConnectedThread.isAlive()) mConnectedThread.cancel();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public boolean onKeyDown(int keycode, KeyEvent event) {
        if (keycode == KeyEvent.KEYCODE_DPAD_CENTER) {
            mConnectedThread.write("C".getBytes());
            picSizeRcvd = true;
            picComing = true;

            return true;
        }
        return super.onKeyDown(keycode, event);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!picComing) {
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                mGeomagnetic = event.values;

            if (connected && (compassReceived || firstTime)) {
                compassReceived = false;
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    x = event.values[0];
                    y = event.values[2];
                    mGravity = event.values;
                }

                if (mGravity != null && mGeomagnetic != null) {
                    float R[] = new float[9];
                    float I[] = new float[9];

                    boolean success = SensorManager.getRotationMatrix(R, I, mGravity,
                            mGeomagnetic);
                    if (success) {
                        float orientation[] = new float[3];
                        SensorManager.getOrientation(R, orientation);
                        azimuth = Math.round(Math.toDegrees(orientation[0]));
                    }
                    //Log.d("AZIMUTH", Boolean.toString(success));
                }

                mConnectedThread.write(("M" + Float.toString(y) + "#" + Float.toString(x) + "#").getBytes());

                mTextView.setText("X: " + String.format("%.2f", x) +
                        "\nY: " + String.format("%.2f", y) +
                        "\nAzi: " + String.format("%.2f", carCompass));

                Bitmap src = BitmapFactory.decodeResource(getResources(), R.drawable.compass);
//                Matrix matrix = new Matrix();
//                // setup rotation degree
//                matrix.postRotate(carCompass);
//                // return new bitmap rotated using matrix
//                mImageView.setImageBitmap(Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true));


                Paint mPaint = new Paint();
                float x1 = 275f;
                float y1 = 275f;
                float radius = 250f;
                float x2 = x1 + (radius * (float)Math.cos(Math.toRadians(carCompass + 270)));
                float y2 = y1 + (radius * (float)Math.sin(Math.toRadians(carCompass + 270)));

                Bitmap tempBitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.RGB_565);
                Canvas tempCanvas = new Canvas(tempBitmap);

                tempCanvas.drawBitmap(src, 0, 0, null);

                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(5);
                mPaint.setColor(Color.RED);
                tempCanvas.drawLine(x1, y1, x2, y2, mPaint);


                mImageView.setImageBitmap(tempBitmap);

                if (picHere && bMap != null) {
                    mImageView.setImageBitmap(bMap);
                    picHere = false;
                }

                firstTime = false;
            }
        }
    }


    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        byte[] writeBuf = (byte[]) msg.obj;
        int begin = (int) msg.arg1;
        int end = (int) msg.arg2;

        switch (msg.what) {
            case 1:
                writeMessage = new String(writeBuf);
                writeMessage = writeMessage.substring(begin, end);
                if (writeMessage.equals("R")) {
                    connected = true;
                    Log.d("CONNECTED", "TRUE");
//                    mConnectedThread.write(("C".getBytes()));
//                    picComing = true;
                }
                if (writeMessage.substring(0, 1).equals("D")) {
                    compassReceived = true;
                    carCompass = Float.parseFloat(writeMessage.substring(1, writeMessage.length()));
                }
                if (picComing && writeMessage.substring(0, 1).equals("P")) {
                    picSize = Integer.parseInt(writeMessage.substring(1, writeMessage.length()));
                   // picComing = false;
                }
                //Log.d("RCVD", writeMessage);
                break;
        }
        }
    };

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
            }
            mmSocket = tmp;
        }

        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                return;
            }

            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            byte[] picBuffer = new byte[20480];
            int begin = 0;
            int bytes = 0;
            int picBytes = 0;
            int index = 0;

            while (true) {
                try {

                    if (!picComing) {
                        bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
                        for (int i = begin; i < bytes; i++) {
                            if (buffer[i] == "#".getBytes()[0]) {
                                mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                                begin = i + 1;
                                if (i == bytes - 1) {
                                    bytes = 0;
                                    begin = 0;
                                }
                            }
                        }
                    }

                    // TODO Sending JPEG over Bluetooth
                    else {

                        picBytes += mmInStream.read(picBuffer, picBytes, 1);

                        if (picSize - picBytes < 50) {
                            Log.d("LEFT", String.format("%d", picSize - picBytes));
                            Log.d("HEX", String.format("%x", picBuffer[picBytes-1]));
                        }


                        if (picBuffer[picBytes - 1] == (byte) 0xff && picBuffer[picBytes] == (byte) 0xd8) {
                            index = picBytes - 1;
                            Log.d("INDEX", String.format("%d", index));
                        }

                        //if (picBytes > 1 && picBuffer[picBytes-2] == (byte)0xff && picBuffer[picBytes-1] == (byte)0xd9) {
                        if(picBytes == picSize) {
                            Log.d("PIC", "RCVD");
                            Log.d("FIRST", String.format("%x", picBuffer[0]) + String.format(" %x", picBuffer[1]));
                            bMap = BitmapFactory.decodeByteArray(picBuffer, 0, picBytes-1);
                           // mImageView.setImageBitmap(bMap);

                            picComing = false;
                            picHere = true;

//                            for (int i = 0; i < picBytes; i++)
//                                Log.d("HEX", String.format("%x", picBuffer[i]));

                            picBytes = 0;
                            picBuffer = new byte[20480];

                        }

                    }
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
}