/**
 * Copyright � 2011 Mike Depinet
 * All rights reserved
 * 
 * This file is distributed as a part of the SAFEMODE application for
 * Android devices.  SAFEMODE is distributed under Apache License 2.0
 * which can be found in full at http://www.apache.org/licenses/LICENSE-2.0
 */

package com.teamPrime.sm.tasks;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.teamPrime.sm.HistoryActivity;
import com.teamPrime.sm.data.ContactDAO;
import com.teamPrime.sm.data.ContactDAO.DataAccessException;
import com.teamPrime.sm.data.ContactData;
import com.teamPrime.sm.history.BlockedCallItem;
import com.teamPrime.sm.history.BlockedTextItem;
import com.teamPrime.sm.history.action.RedialAction;
import com.teamPrime.sm.history.action.ResendTextAction;
import com.teamPrime.sm.history.action.ViewTextAction;

/**
 * BlackListIOTask takes care of calling ContactDAO in the
 * background with the proper contact ids.
 * 
 * @author Mike Depinet
 * @version 1.0
 */
public class BlackListIOTask extends AsyncTask<Void, Void, List<Long>> {
	public static final int WRITE_IDS_MODE = 0;
	public static final int READ_IDS_MODE = 1;
	public static final int HIDE_CONTACTS_MODE = 2; //Note that this must also read ids
	public static final int REVEAL_CONTACTS_MODE = 3;
	
	public static final int SafeMode_BLOCKED_SMS_RESPONSE_CODE = 101;
	
	private static final String fileName = "SAFEMODE_contactIds.bin";
	private static final String smsUri = "content://sms";
	
	private Activity mActivity;
	private int mode;
	private List<Long> contactIds;
	private Handler mHandler;
	private CallInterceptor callInt;
	private SmsInterceptor smsInt;
	
	public BlackListIOTask(Activity activity, List<Long> contactIds, int mode){
		mActivity = activity;
		this.contactIds = contactIds;
		this.mode = mode;
	}
	public void setActivity(Activity activity){
		mActivity = activity;
	}
	
	 //Runs on main thread.
	 @Override
	 protected void onPreExecute() {
		 mHandler = new Handler();
	 }

  // Runs on main thread.
	 @Override
	 protected void onProgressUpdate(Void... arg0) {
	 }

	 // Runs on main thread.
	 @Override
	 protected void onPostExecute(List<Long> result) {
	 }
	
	 @Override
	 protected List<Long> doInBackground(Void... arg0) {
		 switch(mode){
		 case WRITE_IDS_MODE:
			 ObjectOutputStream oos = null;
			 try {
				 oos = new ObjectOutputStream(mActivity.openFileOutput(fileName, Activity.MODE_PRIVATE));
				 oos.writeObject(contactIds);
			 } catch (FileNotFoundException e) {
				 Log.e("SAFEMODE - ContactsIO", "Failed to write contact ids", e);
			 } catch (IOException e) {
				 Log.e("SAFEMODE - ContactsIO", "Failed to write contact ids", e);
			 } finally{
				 try{oos.close();}catch(Throwable t){}
			 }
			 return null;
		 case READ_IDS_MODE:
			 readContactIds();
			 return contactIds;
		 case HIDE_CONTACTS_MODE:
			 readContactIds();
			 
			 int i = 0;
			 try {
				 //Remove from database
				 List<ContactData> blockedData = ContactDAO.hideContacts(mActivity, contactIds);
				 Collection<String> blockedNums = ContactDAO.getPhoneNumbersForContacts(blockedData);
				 
				 //Register receiver to explicitly block outgoing calls
				 IntentFilter filter = new IntentFilter();
			     filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
			     filter.setPriority(IntentFilter.SYSTEM_LOW_PRIORITY+1);
				 mActivity.registerReceiver(callInt = new CallInterceptor(blockedNums), filter);
				 
				 //Register content observer to explicitly block outgoing sms messages
				 mActivity.getContentResolver().registerContentObserver(Uri.parse(smsUri),true, smsInt = new SmsInterceptor(mHandler,blockedNums));
			 } catch (DataAccessException e) {
				 Log.e("SAFEMODE - ContactsIO", "Failed to hide contacts",e);
			 }
			 List<Long> result = new LinkedList<Long>();
			 result.add((long) i);
			 return result;
		 case REVEAL_CONTACTS_MODE:
			 int j = ContactDAO.revealContacts(mActivity);
			 if (callInt != null) mActivity.unregisterReceiver(callInt);
			 if (smsInt != null) mActivity.getContentResolver().unregisterContentObserver(smsInt);
			 List<Long> result2 = new LinkedList<Long>();
			 result2.add((long) j);
			 return result2;
		 }
		 return null;
	 }
	 
	 @SuppressWarnings("unchecked")
	 private void readContactIds(){
		 ObjectInputStream ois = null;
		 try{
			 ois = new ObjectInputStream(mActivity.openFileInput(fileName));
			 contactIds = (List<Long>) ois.readObject();
		 } catch(IOException e) {
			 Log.e("SAFEMODE - ContactsIO", "Failed to read contact ids", e);
		 } catch (ClassNotFoundException e) {
			 Log.e("SAFEMODE - ContactsIO", "Failed to read contact ids", e);
		 } finally{
				try{ois.close();}catch(Throwable t){}
		 }
	 }
	 
	 

		public class CallInterceptor extends BroadcastReceiver{
			private Collection<String> blockedNums;
			
			public CallInterceptor(Collection<String> blockedNums){
				this.blockedNums = blockedNums;
			}
			@Override
			public void onReceive(Context c, Intent i){
				if (i.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)){
					String phoneNumber = (String) i.getExtras().get(Intent.EXTRA_PHONE_NUMBER);
					if (isBlocked(phoneNumber, blockedNums)){
						setResultData(null); //Don't make the call
						Toast.makeText(c, "Call blocked by SafeMode", Toast.LENGTH_SHORT).show();
						RedialAction ra = new RedialAction(phoneNumber);
						HistoryActivity.addItem(c, new BlockedCallItem(null, phoneNumber, ra));
					}
				}
			}
		}
		
		public class SmsInterceptor extends ContentObserver{
			private Collection<String> blockedNums;
			private final long startTime = System.currentTimeMillis(); //Don't delete messages sent before this
			
			public SmsInterceptor(Handler handler, Collection<String> blockedNums) {
				super(handler);
				this.blockedNums = blockedNums;
			}
			
			@Override
			public void onChange(boolean selfChange){
				Uri uriSMSURI = Uri.parse(smsUri);
				Cursor cur = mActivity.getContentResolver().query(uriSMSURI, new String[]{"protocol","address","_id","body","date"}, null, null, null);
				while(cur.moveToNext()){
					String protocol = cur.getString(0);
					String number = cur.getString(1);
					if (protocol != null) continue;
					if (isBlocked(number, blockedNums)){
						String ID = cur.getString(2);
						String body = cur.getString(3);
						long recTime = cur.getLong(4);
						if (recTime < startTime) continue;
						mActivity.getContentResolver().delete(uriSMSURI, "_id=?", new String[]{ID}); //Don't send it hopefully
						Toast.makeText(mActivity, "SMS blocked by SafeMode", Toast.LENGTH_SHORT).show();
						ViewTextAction vta = new ViewTextAction(number, body, SafeMode_BLOCKED_SMS_RESPONSE_CODE);
						ResendTextAction rta = new ResendTextAction(number, body);
						HistoryActivity.addItem(mActivity, new BlockedTextItem(null,number,vta,rta));
						break; //Don't delete all the messages that have been sent!
					}
				}
			}
		}
		
		private boolean isBlocked(String number, Collection<String> blockedNums){
			for (String blocked : blockedNums){
				if (standardize(blocked).equals(standardize(number))) return true;
			}
			return false;
		}
		private String standardize(String number){
			String result = number.replaceAll("[\\s()+-]+", ""); //Strip whitespace, (, ), +, or -
			if (result.length() > 10) result = result.substring(result.length()-10); //Only get last 10 digits
			return result;
		}
}
