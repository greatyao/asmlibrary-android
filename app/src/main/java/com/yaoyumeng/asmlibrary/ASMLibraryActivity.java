package com.yaoyumeng.asmlibrary;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.core.Scalar;
import org.opencv.core.Point;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.objdetect.CascadeClassifier;
import com.yaoyumeng.asmlibrary.R;
import com.yaoyumeng.asmlibrary.ASMFit;

import android.os.Handler;
import android.os.Message;   
import android.hardware.Camera;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.net.Uri;  
import android.database.Cursor;    
import android.graphics.Bitmap;    
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.widget.Toast;

public class ASMLibraryActivity extends Activity implements CvCameraViewListener2
{    
	private static final String    TAG = "ASMLibraryActivity";
    
	private Button mSwitchCameraButton = null;
	private Button mCameraButton = null;
	private Button mGallaryButton = null;
	private Button mAboutButton = null;
	private Button mAvatarButton = null;
	private TextView mCameraText = null;
	private TextView mGallaryText = null;
    private Mat                    	mRgba;
    private Mat                    	mSmallRgba;
    private Mat                    	mGray;
    private ASMApplication			mApp;
    private int						m_NumberOfCameras = 0;
    private long				   	mFrame;
    private boolean					mFlag;
    private boolean					mFastDetect = false;
    private boolean					mCamera = true;
    private boolean					mAvatar = false;
    private int						mCameraIdx = 0;
    private int 					mSDKVersion = Integer.parseInt(android.os.Build.VERSION.SDK); 
    private Mat						mShape;
    private static final Scalar 	mRedColor = new Scalar(255, 0, 0);
    private static final Scalar 	mCyanColor = new Scalar(0, 255, 255);
    private CameraView   			mCameraView;
    private MatrixImageView   		mImageView;
    private ProgressDialog			mProgress;
    
    public ASMLibraryActivity() 
    {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }
    
    private static final int MSG_SUCCESS = 0;  
    private static final int MSG_FAILURE = 1;
    private static final int MSG_PROGRESS = 2;
    private static final int MSG_STATUS = 3;
    
    private Handler mHandler = new Handler() {  
    	public void handleMessage (Message msg) {
    		switch(msg.what) {
    			case MSG_SUCCESS:
    				mFittingDone = true;
    				mProgress.dismiss();
    				mImageView.setImageBitmap((Bitmap) msg.obj);  
    				Toast.makeText(getApplication(), "Fitting Done", Toast.LENGTH_LONG).show();  
    				break; 
    			case MSG_FAILURE:  
    				mFittingDone = true;
    				mProgress.dismiss();
    				Toast.makeText(getApplication(), "Canot detect any face", Toast.LENGTH_LONG).show();  
    				break;  
    			case MSG_STATUS:
    				mProgress.setMessage(msg.arg1 == 0 ? "Detecting" : "Alignment");
					break;
    		}
		}  
    };  

    private String mImageFileName = new String();
    private boolean mFittingDone = false;
    private Bitmap	mBitmap = null;
    private int mScaleFactor = 1;
    
    private void fittingOnStaticImageAsyn(String imgName){
    	mImageFileName = imgName;
    	mFittingDone = false;
       	if(mBitmap != null)
    		mBitmap.recycle();
    	System.gc();
    	
    	int []factors = {1, 2, 4, 8, 16};
    	BitmapFactory.Options opt = new BitmapFactory.Options();
    	for(int i = 0; i < factors.length; i++)
    	{
    		boolean ok = true;
    		try
    		{
    			if(i == 0)
    				mBitmap = BitmapFactory.decodeFile(imgName);
    			else
    			{
    				opt.inSampleSize = factors[i];
    				mBitmap = BitmapFactory.decodeFile(imgName, opt);
    			}
    		}
    		catch(OutOfMemoryError e)
    		{
    			ok = false;
    		}
    		
    		if(ok == true)
    		{
    			mScaleFactor = factors[i];
    			break;
    		}
    	}
    	
    	if(mBitmap == null)
    	{
    		Toast.makeText(mApp.getBaseContext(), "Cannot open image file " + imgName, Toast.LENGTH_LONG).show();
			return;	
    	}
    	
    	mImageView.setImageBitmap(mBitmap);
    	mProgress = ProgressDialog.show(ASMLibraryActivity.this, null, "Loading", true);
    	mProgress.setCancelable(false);
    	
    	new Thread(new Runnable() {  
    		@Override  
    		public void run() {
    			int i = 0;
    			while (!mFittingDone) {
					try {
						i += 4;
						int j = i % 200;
						if(j >= 100) j = 200 - j;
						mHandler.obtainMessage(MSG_PROGRESS, j, 0).sendToTarget(); 
						Thread.sleep(200);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
    		}  
    	}).start();  
    	
    	new Thread(new Runnable() {  
    		@Override  
    		public void run() {
    			Mat image = Highgui.imread(mImageFileName, Highgui.IMREAD_COLOR);
    	    	Mat shapes = new Mat();
    	    	mHandler.obtainMessage(MSG_STATUS, 0, 0).sendToTarget(); 
    	    	boolean flag = ASMFit.detectAll(image, shapes);
    	    	
    	    	if(flag == false){
    	    		mHandler.obtainMessage(MSG_FAILURE).sendToTarget();  
    	    		return;
    	    	}
    	    	
    	    	mHandler.obtainMessage(MSG_STATUS, 1, 0).sendToTarget(); 
    	    	ASMFit.fitting(image, shapes, 30);
            	for(int i = 0; i < shapes.rows(); i++){
            		if(mAvatar)
            		{
                		ASMFit.drawAvatar(image, shapes.row(i), false);
                		continue;
            		}
            		for(int j = 0; j < shapes.row(i).cols()/2; j++){
            			double x = shapes.get(i, 2*j)[0];
        				double y = shapes.get(i, 2*j+1)[0];
        				Point pt = new Point(x, y);
        				
        				Core.circle(image, pt, 3, mCyanColor, 2);
            		}
            	}
            	
            	Imgproc.cvtColor(image, image, Imgproc.COLOR_RGB2BGR);
            	if(mScaleFactor == 1)
            		Utils.matToBitmap(image, mBitmap);
            	else
            	{
            		Mat image2 = new Mat(mBitmap.getHeight(), mBitmap.getWidth(), image.type());
            		Imgproc.resize(image, image2, image2.size());
            		Utils.matToBitmap(image2, mBitmap);
            		image2.release();
            	}
            	
            	image.release();
            	shapes.release();
    	    	mHandler.obtainMessage(MSG_SUCCESS, mBitmap).sendToTarget();
    		}  
    	}).start();  
    }
    
    
    private static final String flagKey = "camera";
    
    @Override
    public void onSaveInstanceState(Bundle outState)
    {
    	Log.i(TAG, "called onSaveInstanceState");
    	super.onSaveInstanceState(outState);
    	outState.putBoolean(flagKey, mCamera);
    }

	/** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState) 
	{
		Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        mApp = (ASMApplication)getApplication();
        m_NumberOfCameras = Camera.getNumberOfCameras();
        mFrame = 0;
        mFlag = false;
        if(savedInstanceState != null)
        	mCamera = savedInstanceState.getBoolean(flagKey, true);
        
        //setContentView(R.layout.main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().setFormat(PixelFormat.UNKNOWN);
		
		setContentView(R.layout.asmlibrary_surface_view);  
        mCameraView = (CameraView) findViewById(R.id.java_surface_back_view);
        mCameraView.setVisibility(SurfaceView.GONE);
        mCameraView.setCvCameraViewListener(this);
        mCameraView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        mImageView = (MatrixImageView)findViewById(R.id.image_view);
        mImageView.setVisibility(SurfaceView.GONE);
        mImageView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                
        LayoutInflater controlInflater = LayoutInflater.from(getBaseContext());
		View viewControl = controlInflater.inflate(R.layout.main, null);
		LayoutParams layoutParamsControl = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		addContentView(viewControl, layoutParamsControl);
		
		mCameraButton = (Button) findViewById(R.id.cameraButton);
		mCameraText = (TextView)findViewById(R.id.cameraLabel);
		mCameraButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if(!mCamera)
				{
					mCamera = true;
					mCameraView.setVisibility(SurfaceView.VISIBLE);
	            	mImageView.setVisibility(SurfaceView.GONE);
	            	mSwitchCameraButton.setVisibility(m_NumberOfCameras <= 1 ? View.GONE : View.VISIBLE); 
	            	mCameraView.enableView();
				}
			}
		});
		
		mGallaryButton = (Button) findViewById(R.id.gallaryButton);
		mGallaryText = (TextView)findViewById(R.id.gallaryLabel);
		mGallaryButton.setVisibility(!mCamera ? View.GONE : View.VISIBLE);
		mGallaryText.setVisibility(!mCamera ? View.GONE : View.VISIBLE);
		mGallaryButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				mCamera = false;
				mCameraView.setVisibility(SurfaceView.GONE);
            	mImageView.setVisibility(SurfaceView.VISIBLE);
            	mCameraView.disableView();
            	mSwitchCameraButton.setVisibility(View.GONE);
				chooseImageFromAlbum();
			}
		});
		
		mSwitchCameraButton = (Button) findViewById(R.id.switchCameraButton);
		if(m_NumberOfCameras <= 1)
			mSwitchCameraButton.setVisibility(View.GONE);
		mSwitchCameraButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (m_NumberOfCameras > 1) 
	        	{
	        		mCameraIdx = 1 - mCameraIdx;
				    mCameraView.setCameraIndex(mCameraIdx);
	    		}
			}
		});
		
		mAboutButton = (Button) findViewById(R.id.aboutButton);
		mAboutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent activity = new Intent(ASMLibraryActivity.this, AboutActivity.class);
	    		startActivity(activity);
			}
		});
		
		mAvatarButton = (Button) findViewById(R.id.avatarButton);
		mAvatarButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				mAvatar = !mAvatar;
			}
		});

    }
	
	@Override
    public void onPause()
    {
		Log.i(TAG, "called onPause");
        super.onPause();
        if (mCameraView != null)
        	mCameraView.disableView();
    }

    @Override
    public void onResume()
    {
    	Log.i(TAG, "called onResume");
    	super.onResume();
        
        if(mCamera)
        {
        	mCameraView.setVisibility(SurfaceView.VISIBLE);
	        mCameraView.enableView();
	        mFrame = 0;
	        mFlag = false;
        }
        else
        {
        	 mImageView.setVisibility(SurfaceView.VISIBLE);
        }
    }
    
    @Override
    public void onDestroy() 
    {
    	Log.i(TAG, "called onDestroy");
        super.onDestroy();
        if (mCameraView != null)
        	mCameraView.disableView();
    }
    
    public boolean onKeyDown(int keyCode, KeyEvent event) {
		boolean flag = true;
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// �Ƿ��˳�Ӧ��
			UIHelper.exitAppOnKeyBack(this);
		} else {
			flag = super.onKeyDown(keyCode, event);
		}
		return flag;
	}
    
    private static final int SELECT_PICTURE = 1;
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data); 
    	if(resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {           
	        Uri uri = data.getData();	                
	        if( uri == null ) {
	            return;
	        }
	        
	        // try to retrieve the image from the media store first
	        // this will only work for images selected from gallery
	        String[] projection = { MediaStore.Images.Media.DATA };
	        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

	        String path = null;
	        if( cursor != null ){
	            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	            if(cursor.moveToFirst())
	            	path = cursor.getString(column_index);
            	cursor.close();
	        }
	        if(path == null)
	        	path = uri.getPath();
	        
	        fittingOnStaticImageAsyn(path);
	    }
    }
    
    private void chooseImageFromAlbum()
    {
    	Intent intent = new Intent(Intent.ACTION_PICK);
    	intent.setType("image/*");
    	startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);  
    }

    public void onCameraViewStarted(int width, int height) 
    {
    	mGray = new Mat();
        mRgba = new Mat();
        mSmallRgba = new Mat();
        mShape = new Mat();
        mFrame = 0;
        mFlag = false;
    }

    public void onCameraViewStopped() 
    {
    	mGray.release();
        mRgba.release();
        mSmallRgba.release();
        mShape.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) 
    {
        mRgba = inputFrame.rgba();
        if(mCameraIdx == 1)
			Core.flip(mRgba, mRgba, 1);
        Imgproc.cvtColor(mRgba, mGray, Imgproc.COLOR_RGBA2GRAY);
        int w = mRgba.cols()/4;
    	int h = mRgba.rows()/4;
    	if(mSmallRgba.rows() != h)
    		mSmallRgba.create(w, h, mRgba.type());
    	Mat submat = mRgba.submat(new Rect(3*w, 0, w, h));
        if(mAvatar)
        	Imgproc.resize(mRgba, mSmallRgba, new Size(w, h));
    	
        
        long lMilliStart = System.currentTimeMillis();
        CascadeClassifier javaCascade = mApp.mJavaCascade;
        
        if(mFrame == 0 || mFlag == false)
		{
        	Mat detShape = new Mat();
        	int iFaceIndex = 0;
        	if(mFastDetect || javaCascade == null)
				mFlag = ASMFit.fastDetectAll(mGray, detShape);
			else
			{
				int height = mGray.rows();
                double faceSize = (double) height * 0.4; 
                Size sSize = new Size(faceSize, faceSize);
                MatOfRect faces = new MatOfRect();
                javaCascade.detectMultiScale(mGray, faces, 1.1, 2, 2, sSize, new Size());
                Rect[] facesArray = faces.toArray();
                if(facesArray.length == 0) mFlag = false;
                else
                {
                	mFlag = true;
                	detShape = new Mat(facesArray.length, 4, CvType.CV_64FC1);
                	int iMaxFaceHeight = facesArray[0].height;
                	for(int i = 0; i < facesArray.length; i++)
                	{
                		detShape.put(i, 0, facesArray[i].x);
                		detShape.put(i, 1, facesArray[i].y);
                		detShape.put(i, 2, facesArray[i].x + facesArray[i].width);
                		detShape.put(i, 3, facesArray[i].y + facesArray[i].height);
                		
                		Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), mRedColor, 3);
                		
                		if (iMaxFaceHeight < facesArray[i].height) 
                		{
                        	iMaxFaceHeight = facesArray[i].height;
                        	iFaceIndex = i;
                        }
                	}
                
                	ASMFit.initShape(detShape);
                }
			}
			if(mFlag)	mShape = detShape.row(iFaceIndex);
		}
			
		if(mFlag) 
		{
			mFlag = ASMFit.videoFitting(mGray, mShape, mFrame, 20);
		}
		
		if(mFlag)
		{
			if(mAvatar)
			{
				ASMFit.drawAvatar(mRgba, mShape);
				mSmallRgba.copyTo(submat);
			}
			else
			{
				int nPoints = mShape.row(0).cols()/2;
				for(int i = 0; i < nPoints; i++)
				{ 
					double x = mShape.get(0, 2*i)[0];
					double y = mShape.get(0, 2*i+1)[0];
					Point pt = new Point(x, y);
					
					Core.circle(mRgba, pt, 3, mCyanColor, 2);
				}
			}
		}
		else
		{
			if(mAvatar)
			{
				mRgba.setTo(new Scalar(0, 0, 0));
				mSmallRgba.copyTo(submat);
			}
		}
		
		long lMilliNow = System.currentTimeMillis();	
    	String string = String.format("FPS: %2.1f", 1000.0f / (float)(lMilliNow - lMilliStart));
        double dTextScaleFactor = 1.8;
	    Core.putText(mRgba, string, new Point(10, dTextScaleFactor*60*1), 
   			 Core.FONT_HERSHEY_SIMPLEX, dTextScaleFactor, mCyanColor, 2);
		
		mFrame ++;
		return mRgba;
    }
}