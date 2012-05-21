package com.teamPrime.sm.history;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.telephony.PhoneNumberUtils;

import com.teamPrime.sm.HistoryActivity;
import com.teamPrime.sm.R;

public class BlockedTextItem extends HistoryItem {
	private static final long serialVersionUID = 3581778324090032221L;
	
	private Date creationDate = new Date();
	private String phoneNumber;
	
	public BlockedTextItem(HistoryActivity activity, String phoneNumber, HistAction defaultAction, HistAction... acts) {
		super(activity, defaultAction, acts);
		this.phoneNumber = phoneNumber;
	}
	
	public String toString(){
		return (activity==null?"Blocked text to ":activity.getString(R.string.hist_bText_bTextTo)) + phoneNumber + "\n" + new SimpleDateFormat(HistoryItem.DATE_FORMAT).format(creationDate);
	}
	
	public String getTitle(){
		return activity==null?"Blocked Text":activity.getString(R.string.hist_bText_bText);
	}
	
	public String getDescription(){
		//should be changed to contact name
		return PhoneNumberUtils.formatNumber(phoneNumber);
	}
}
