package com.yaoyumeng.asmlibrary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.objdetect.CascadeClassifier;
import org.opencv.android.OpenCVLoader;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class ASMApplication extends Application {
	
	private static final String    TAG                 = "ASMApplication";
	private File                   	mCascadeFile;
    private File                   	mFastCascadeFile;
    private File                   	mModelFile;
    private File                   	mAAMModelFile;
    public  CascadeClassifier 		mJavaCascade;
	
	private File getSourceFile(int id, String name, String folder)
    {
    	File cascadeDir = getDir(folder, Context.MODE_PRIVATE);
        File file = new File(cascadeDir, name);
        boolean existed = true;
        FileInputStream fis = null;
        try {
        	fis=new FileInputStream(file);
		} catch (FileNotFoundException e) {
			existed = false;
		} finally{
			try{
				fis.close();
			}catch(Exception e){
			}
		}
        if(existed == true)
        	return file;
        
		try 
		{
    		InputStream is = getResources().openRawResource(id);
            FileOutputStream os = new FileOutputStream(file);
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) 
            {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
		}catch (IOException e) 
		{
            e.printStackTrace();
            Log.e(TAG, "Failed to load file " + name + ". Exception thrown: " + e);
        }
            
        return file;	
	}
    
    private void initialize()
    {
    	Log.i(TAG, "Application " + this.getClass());
    	 
        mModelFile = getSourceFile(R.raw.my68_1d, "my68_1d.amf", "model");
        if(mModelFile != null)
        	ASMFit.nativeReadModel(mModelFile.getAbsolutePath());   

        mAAMModelFile = getSourceFile(R.raw.my68, "my68.aam", "model");
        if(mAAMModelFile != null)
        	ASMFit.nativeReadAAMModel(mAAMModelFile.getAbsolutePath());   
        
        mCascadeFile = getSourceFile(R.raw.haarcascade_frontalface_alt2, 
        		"haarcascade_frontalface_alt2.xml", "model");
        if(mCascadeFile != null)
        {
        	ASMFit.nativeInitCascadeDetector(mCascadeFile.getAbsolutePath());
        	mJavaCascade = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        	if (mJavaCascade.empty())
                mJavaCascade = null;
        }

        mFastCascadeFile = getSourceFile(R.raw.lbpcascade_frontalface, 
        		"lbpcascade_frontalface.xml", "model");
        if(mFastCascadeFile != null)
        	ASMFit.nativeInitFastCascadeDetector(mFastCascadeFile.getAbsolutePath());
    }
    
    public ASMApplication()
    {
    	Log.i(TAG, "Instantiated new " + this.getClass());
    }
    
	@Override
	public void onCreate()
	{
		Log.i(TAG, "called onCreate");
		super.onCreate();
		
		if(OpenCVLoader.initDebug())
        	initialize();
	}
	
	@Override
	public void onTerminate()
	{
		Log.i(TAG, "called onTerminate");
		super.onTerminate();
    }
}
