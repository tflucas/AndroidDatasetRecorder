package com.example.androiddatasetrecorder;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.core.app.ActivityCompat;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends Activity
{
    // IMU
    private SensorManager sensorManager;

    // Camera
    private Camera mCamera;
    private CameraPreview mPreview;

    // GPS
    private LocationManager lm;


    private Context myContext;

    private FrameLayout cameraPreview;
    private TextView accView;
    private TextView gyroView;
    private TextView longitudeView;
    private TextView latitudeView;
    private TextView altitudeView;

    private ImageButton startRecord;


    String fileName;
    String externalDir;

    PrintWriter writerAcc = null;
    PrintWriter writerGyro = null;
    PrintWriter writerRotVector = null;
    PrintWriter writerGravity = null;
    PrintWriter writerGPS = null;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private static final int REQUEST_CAMERA = 2;
    private static String[] PERMISSIONS_CAMERA = {"android.permission.CAMERA"};
    private static final int REQUEST_ACCESS_FINE_LOCATION = 3;
    private static String[] PERMISSIONS_ACCESS_FINE_LOCATION = {"android.permission.ACCESS_FINE_LOCATION"};

    // Set a resolution for images
    int quality = 2;

    // flag for recording data
    boolean bStartRecording = false;


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        myContext = this;
        verifyStoragePermissions(this);


        /****************** IMU and rotation vector ***************/
        // Maximum frequency of acc and gyro : 500hz
        int imuRate = 4000;  // time in us
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);  // Hardware sensor
        sensorManager.registerListener(listener_acc, accelerometer, imuRate);
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);          // Hardware sensor
        sensorManager.registerListener(listener_gyro, gyroscope, imuRate);
        Sensor rotVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);    // Software sensor
        sensorManager.registerListener(listener_rot, rotVector, imuRate);                  // Maximum frequency of rotation vector: 100hz
        Sensor gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);              // Software sensor
        sensorManager.registerListener(listener_gra, gravity, imuRate);                    // Maximum frequency of gravity: 100hz

        accView = (TextView) findViewById(R.id.acc);
        gyroView = (TextView) findViewById(R.id.gyro);

        // Control button for recording data
        startRecord = (ImageButton) findViewById(R.id.button_start);
        startRecord.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Toast.makeText(MainActivity.this, "Start recording!", Toast.LENGTH_LONG).show();

                bStartRecording = true;
                if (bStartRecording)
                {
                    Log.e("tfl", "Start recording");
                    mPreview.setRecordImages(bStartRecording);
                }
            }
        });

        // get external direction
        externalDir = getExternalFilesDir(null).toString();

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        fileName = format.format(date);

        /************************* Writing IMU Data **********************/
        //File wallpaperDirectory = new File(Environment.getExternalStorageDirectory().getPath() + "/AndroidDataRecorder/"+ fileName + "/" + "IMU");
        File wallpaperDirectory = new File(getExternalFilesDir(null).toString() + "/AndroidDataRecorder/" + fileName + "/" + "IMU");
        boolean bMkdirs = wallpaperDirectory.mkdirs();
        if (!bMkdirs)
        {
            Log.e("tfl", "Creating Folder is failed!");
        }
        else
        {
            Log.e("tfl", "Creating Folder is successful!");
        }

        String filePathAcc = getExternalFilesDir(null).toString() + "/AndroidDataRecorder/" + fileName + "/" + "IMU" + "/" + "acc.csv";
        String filePathGyro = getExternalFilesDir(null).toString() + "/AndroidDataRecorder/" + fileName + "/" + "IMU" + "/" + "gyro.csv";
        String filePathRotVector = getExternalFilesDir(null).toString() + "/AndroidDataRecorder/" + fileName + "/" + "IMU" + "/" + "rot_vector.csv";
        String filePathGravity = getExternalFilesDir(null).toString() + "/AndroidDataRecorder/" + fileName + "/" + "IMU" + "/" + "gravity.csv";
        try
        {
            writerAcc = new PrintWriter(filePathAcc);
            writerGyro = new PrintWriter(filePathGyro);
            writerRotVector = new PrintWriter(filePathRotVector);
            writerGravity = new PrintWriter(filePathGravity);
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
            Log.e("tfl", "SaveIMUFileError");
        }


        /********************* Camera *************************/
        cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
        mPreview = new CameraPreview(myContext, mCamera, externalDir, fileName);
        cameraPreview.addView(mPreview);


        /********************* GPS *********************/
        longitudeView = (TextView) findViewById(R.id.longitude);
        latitudeView = (TextView) findViewById(R.id.latitude);
        altitudeView = (TextView) findViewById(R.id.altitude);

        // Write GPS data
        String filePathGPS = getExternalFilesDir(null).toString() + "/AndroidDataRecorder/" + fileName + "/" + "GPS.csv";
        try
        {
            writerGPS = new PrintWriter(filePathGPS);
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
            Log.e("tfl", "SaveGPSFileError");
        }

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location lc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        updateGPSShow(lc);

        //设置间隔两秒获得一次GPS定位信息
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, new LocationListener()
        {
            @Override
            public void onLocationChanged(Location location)
            {
                // 当GPS定位信息发生改变时，更新定位
                updateGPSShow(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras)
            {

            }

            @Override
            public void onProviderEnabled(String provider)
            {
                // 当GPS LocationProvider可用时，更新定位
                updateGPSShow(lm.getLastKnownLocation(provider));
            }

            @Override
            public void onProviderDisabled(String provider)
            {
                updateGPSShow(null);
            }
        });

    }


    public static void verifyStoragePermissions(Activity activity)
    {
        try
        {
            //检测是否有写的权限
            int permissionStorage = ActivityCompat.checkSelfPermission(activity, "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permissionStorage != PackageManager.PERMISSION_GRANTED)
            {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }

            //检测是否有相机的权限
            int permissionCamera = ActivityCompat.checkSelfPermission(activity, "android.permission.CAMERA");
            if (permissionCamera != PackageManager.PERMISSION_GRANTED)
            {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_CAMERA, REQUEST_CAMERA);
            }

            //检测是否有GPS的权限
            int permissionLocation = ActivityCompat.checkSelfPermission(activity, "android.permission.ACCESS_FINE_LOCATION");
            if (permissionLocation != PackageManager.PERMISSION_GRANTED)
            {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_ACCESS_FINE_LOCATION, REQUEST_ACCESS_FINE_LOCATION);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    private int findBackFacingCamera()
    {
        int cameraId = -1;
        // Search for the back facing camera
        // get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        // for every camera check
        for (int i = 0; i < numberOfCameras; i++)
        {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_BACK)
            {
                cameraId = i;
                break;
            }
        }

        return cameraId;
    }


    public void onResume()
    {
        super.onResume();
        if (!checkCameraHardware(myContext))
        {
            Toast toast = Toast.makeText(myContext, "Phone doesn't have a camera!", Toast.LENGTH_LONG);
            toast.show();
            finish();
        }
        if (mCamera == null)
        {
            mCamera = Camera.open(findBackFacingCamera());
            mPreview.refreshCamera(mCamera);
        }

    }


    @Override
    protected void onPause()
    {
        super.onPause();
        // when on Pause, release camera in order to be used from other
        // applications
        releaseCamera();
    }


    private boolean checkCameraHardware(Context context)
    {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
        {
            // this device has a camera
            return true;
        }
        else
        {
            // no camera on this device
            return false;
        }
    }


    // Acc
    private SensorEventListener listener_acc = new SensorEventListener()
    {
        @Override
        public void onSensorChanged(SensorEvent event)
        {

            long timestamp = event.timestamp;

            float acc_x = event.values[0];
            float acc_y = event.values[1];
            float acc_z = event.values[2];

            accView.setText("acc: " + String.format("%.2f", acc_x) + " " + String.format("%.2f", acc_y) + " " + String.format("%.2f", acc_z));

            if(bStartRecording)
            {
                writerAcc.println(timestamp + "," + acc_x + "," + acc_y + "," + acc_z);
                writerAcc.flush();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy)
        {

        }
    };


    // Gyro
    private SensorEventListener listener_gyro = new SensorEventListener()
    {
        @Override
        public void onSensorChanged(SensorEvent event)
        {
            long timestamp = event.timestamp;

            float gyro_x = event.values[0];
            float gyro_y = event.values[1];
            float gyro_z = event.values[2];

            gyroView.setText("gyro: " + String.format("%.2f", gyro_x) + " " + String.format("%.2f", gyro_y) + " " + String.format("%.2f", gyro_z));

            if(bStartRecording)
            {
                writerGyro.println(timestamp + "," + gyro_x + "," + gyro_y + "," + gyro_z);
                writerGyro.flush();
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy)
        {

        }
    };


    // rotation vector
    private SensorEventListener listener_rot = new SensorEventListener()
    {
        @Override
        public void onSensorChanged(SensorEvent event)
        {
            long timestamp = event.timestamp;

            float rot_x = event.values[0];
            float rot_y = event.values[1];
            float rot_z = event.values[2];
            float rot_w = event.values[3];
            float rot_accuracy = event.values[4];

            if(bStartRecording)
            {
                writerRotVector.println(timestamp + "," + rot_x + "," + rot_y + "," + rot_z + "," + rot_w);
                writerRotVector.flush();
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy)
        {

        }
    };


    // Gravity
    private SensorEventListener listener_gra = new SensorEventListener()
    {
        @Override
        public void onSensorChanged(SensorEvent event)
        {
            long timestamp = event.timestamp;

            float gra_x = event.values[0];
            float gra_y = event.values[1];
            float gra_z = event.values[2];


            if(bStartRecording)
            {
                writerGravity.println(timestamp + "," + gra_x + "," + gra_y + "," + gra_z);
                writerGravity.flush();
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy)
        {

        }
    };


    private void releaseCamera()
    {
        // stop and release camera
        if (mCamera != null)
        {
            mCamera.release();
            mCamera = null;
        }
    }


    private void updateGPSShow(Location location)
    {
        if (location != null)
        {
            long timestamp = location.getElapsedRealtimeNanos();

            double longitude = location.getLongitude();
            double latitude = location.getLatitude();
            double altitude = location.getAltitude();


            StringBuilder sbLongitude = new StringBuilder();
            sbLongitude.append("Longitude: " + String.format("%.8f", longitude) + "\n");
            longitudeView.setText(sbLongitude.toString());
            StringBuilder sbLatitude = new StringBuilder();
            sbLatitude.append("Latitude: " + String.format("%.8f", latitude) + "\n");
            latitudeView.setText(sbLatitude.toString());
            StringBuilder sbAltitude = new StringBuilder();
            sbAltitude.append("Altitude: " + String.format("%.3f", altitude) + "\n");
            altitudeView.setText(sbAltitude.toString());

            Log.e("tfl", String.valueOf(timestamp));

            if(bStartRecording)
            {
                writerGPS.println(timestamp + "," + longitude + "," + latitude + "," + altitude);
                writerGPS.flush();
            }
        }
    }


    /* ---------------------- Sensor data ------------------- */
    String[] options = {"1080p","720p","480p"};

    public void addQuality(View view)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String setting = new String();
        if(quality == 0)
        {
            setting = "1080p";
        }
        else if(quality == 1)
        {
            setting = "720p";
        }
        else if(quality == 2)
        {
            setting = "480p";
        }

        if(!bStartRecording)
        {
            builder.setTitle("Pick Quality, Current setting: " + setting).setItems(options, new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int which)
                {
                    // The 'which' argument contains the index position
                    // of the selected item
                    if(which == 0)
                    {
                        quality = 0;
                        mPreview.setQualityImages(quality);
                        mPreview.refreshCamera(mCamera);
                    }
                    else if (which == 1)
                    {
                        quality = 1;
                        mPreview.setQualityImages(quality);
                        mPreview.refreshCamera(mCamera);
                    }
                    else if (which == 2)
                    {
                        quality = 2;
                        mPreview.setQualityImages(quality);
                        mPreview.refreshCamera(mCamera);
                    }
                }
            });
            builder.show();
        }

    }


}