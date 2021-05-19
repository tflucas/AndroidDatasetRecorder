package com.example.androiddatasetrecorder;


import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.graphics.YuvImage;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import java.io.*;


public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback
{
    private SurfaceHolder mHolder;
    private Camera mCamera;

    private String externalDir;
    private String rgbFileName;

    PrintWriter writerImgTimestamp = null;

    boolean bStartRecording = false;
    int quality = 2;

    public CameraPreview(Context context, Camera camera, String path, String fileName)
    {
        super(context);
        mCamera = camera;
        // Install a SurfaceHolder.Callback so we get notified when the underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        externalDir = path;
        rgbFileName = fileName;


        File wallpaperDirectory = new File(externalDir + "/AndroidDataRecorder/" + rgbFileName);
        wallpaperDirectory.mkdirs();
        String filePathImgTimestamp = externalDir + "/AndroidDataRecorder/" + rgbFileName + "/" + "Img.csv";
        try
        {
            writerImgTimestamp = new PrintWriter(filePathImgTimestamp);
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
            Log.e("tfl","SaveImgTimestampError");
        }

    }

    public void surfaceCreated(SurfaceHolder holder)
    {
        try
        {
            // create the surface and start camera preview
            if (mCamera == null)
            {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            }
        }
        catch (IOException e)
        {
            Log.d(VIEW_LOG_TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void refreshCamera(Camera camera)
    {
        if (mHolder.getSurface() == null)
        {
            // preview surface does not exist
            return;
        }
        // stop preview before making changes
        try
        {
            mCamera.stopPreview();
        }
        catch (Exception e)
        {
            // ignore: tried to stop a non-existent preview
        }
        // set preview size and make any resize, rotate or
        // reformatting changes here
        // start preview with new settings
        setCamera(camera);

        try
        {
            Camera.Parameters parameters = camera.getParameters();
            //parameters.setPreviewFrameRate(24);
            //parameters.setPreviewFpsRange(20, 30);
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            if(quality == 0)
                parameters.setPreviewSize(1920, 1080);  // 1080p
            else if(quality == 1)
                parameters.setPreviewSize(1280, 720);   // 720p
            else if(quality == 2)
                parameters.setPreviewSize(640, 480);    // 480p
            mCamera.setParameters(parameters);
            mCamera.setPreviewDisplay(mHolder);

            mCamera.setPreviewCallback(new Camera.PreviewCallback()
            {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera)
                {
                    // TODO Auto-generated method stub
                    Size size = camera.getParameters().getPreviewSize();


                    YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                    String timeStamp = String.valueOf(SystemClock.elapsedRealtimeNanos());
                    try
                    {
                        if(bStartRecording)
                        {
                            writerImgTimestamp.println(timeStamp);
                            writerImgTimestamp.flush();

                            File wallpaperDirectory = new File(externalDir + "/AndroidDataRecorder/" + rgbFileName + "/" + "rgb");
                            wallpaperDirectory.mkdirs();
                            FileOutputStream fos = new FileOutputStream(externalDir + "/AndroidDataRecorder/" + rgbFileName + "/" + "rgb" + "/" + timeStamp + ".jpg");
                            yuvImage.compressToJpeg(new Rect(0, 0, size.width, size.height), 100, fos);
                            fos.close();
                        }

                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                        Log.e("tfl","SaveImageFileError");
                    }


                    /*
                    YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    yuvImage.compressToJpeg(new Rect(0, 0, size.width, size.height), 100, os);
                    byte[] jpegByteArray = os.toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
                    String timeStamp = String.valueOf(System.nanoTime());
                    try
                    {
                        //FileOutputStream fos = new FileOutputStream(Environment.getExternalStorageDirectory().getPath() + "/elab/" + timeStampFile + "/" + timeStamp + ".png");
                        File wallpaperDirectory = new File(Environment.getExternalStorageDirectory().getPath()+"/elab/"+"checkPreviewRate");
                        wallpaperDirectory.mkdirs();
                        FileOutputStream fos = new FileOutputStream(Environment.getExternalStorageDirectory().getPath() + "/elab/" + "checkPreviewRate" + "/" + timeStamp + ".png");
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                        Log.e("tfl","SaveFileError");
                    }
                    */

                }
            });

            mCamera.startPreview();
        }
        catch (Exception e)
        {
            Log.d(VIEW_LOG_TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
    {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        refreshCamera(mCamera);
    }

    public void setCamera(Camera camera)
    {
        //method to set a camera instance
        mCamera = camera;
    }

    public void setRecordImages(boolean bRecord)
    {
        bStartRecording = bRecord;
    }

    public void setQualityImages(int q)
    {
        quality = q;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        // TODO Auto-generated method stub
        // mCamera.release();

    }
}