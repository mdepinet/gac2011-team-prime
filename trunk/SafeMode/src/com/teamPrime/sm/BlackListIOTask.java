package com.teamPrime.sm;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class BlackListIOTask extends AsyncTask<Void, Void, Void> {
	private Activity mActivity;
	private Map<Long, Triple<String,Integer,String>[]> blackMap;
	private Map<Long, Triple<String,Integer,String>[]> fullMap;
	private int updateDB;
	
	private static final String BL_FILENAME = "Blacklist.bin";
	private static final String FULL_FILENAME = "Contacts.bin";

    public BlackListIOTask(Activity activity, Map<Long, Triple<String,Integer,String>[]> fullMap, Map<Long, Triple<String,Integer,String>[]> blackMap, int updateDB) {
        mActivity = activity;
        this.blackMap = blackMap;
        this.fullMap = fullMap;
        this.updateDB = updateDB;
    }

    // Can change activity this task points to.
    // e.g. when activity recreated after orientation change.
    public void setActivity(Activity activity) {
        mActivity = activity;
    }

    // Runs on main thread.
    @Override
    protected void onPreExecute() {
    }

    // Runs on main thread.
    @Override
    protected void onProgressUpdate(Void... arg0) {
    }

    // Runs on main thread.
    @Override
    protected void onPostExecute(Void result) {
    }
	
	@SuppressWarnings("unchecked")
	@Override
	protected Void doInBackground(Void... arg0) {
		if (blackMap != null || fullMap != null){ //Write mode
			ObjectOutputStream oos = null;
			if (blackMap != null){
				try {
					oos = new ObjectOutputStream(mActivity.openFileOutput(BL_FILENAME, Context.MODE_PRIVATE));
					oos.writeObject(blackMap);
				} catch (FileNotFoundException e) {
					Log.e("SAFEMODE", null, e);
				} catch (IOException e) {
					Log.e("SAFEMODE", null, e);
				}
				finally{
					try{oos.close();}catch(Throwable t){}
				}
			}
			if (fullMap != null){
				try {
					oos = new ObjectOutputStream(mActivity.openFileOutput(FULL_FILENAME, Context.MODE_PRIVATE));
					oos.writeObject(fullMap);
				} catch (FileNotFoundException e) {
					Log.e("SAFEMODE", null, e);
				} catch (IOException e) {
					Log.e("SAFEMODE", null, e);
				}
				finally{
					try{oos.close();}catch(Throwable t){}
				}
			}
		}
		else{ //Read mode
			ObjectInputStream ois = null;
			if (blackMap == null){
				try{
					ois = new ObjectInputStream(mActivity.openFileInput(BL_FILENAME));
					blackMap = (Map<Long, Triple<String,Integer,String>[]>) ois.readObject();
				} catch(IOException e) {
					Log.e("SAFEMODE", null, e);
				} catch (ClassNotFoundException e) {
					Log.e("SAFEMODE", null, e);
				}
				finally{
					try{ois.close();}catch(Throwable t){}
				}
			}
			if (fullMap == null){
				try{
					ois = new ObjectInputStream(mActivity.openFileInput(FULL_FILENAME));
					fullMap = (Map<Long, Triple<String,Integer,String>[]>) ois.readObject();
				} catch(IOException e) {
					Log.e("SAFEMODE", null, e);
				} catch (ClassNotFoundException e) {
					Log.e("SAFEMODE", null, e);
				}
				finally{
					try{ois.close();}catch(Throwable t){}
				}
			}
			
			switch (updateDB){
			case 0:
				return null;
			case 1:
				ContactDAO.revealNumbers(fullMap, mActivity.getContentResolver());
				ContactDAO.hideNumbers(blackMap, mActivity.getContentResolver());
				return null;
			case 2:
				ContactDAO.revealNumbers(blackMap, mActivity.getContentResolver());
				ContactDAO.hideNumbers(fullMap, mActivity.getContentResolver());
				return null;
			case 3:
				ContactDAO.revealNumbers(fullMap, mActivity.getContentResolver());
				return null;
			case 4:
				ContactDAO.revealNumbers(blackMap, mActivity.getContentResolver());
				return null;
			default:
				return null;
			}
		}
		return null;
	}
	
}
