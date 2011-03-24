/*
 * GT-Mconf: Multiconference system for interoperable web and mobile
 * http://www.inf.ufrgs.br/prav/gtmconf
 * PRAV Labs - UFRGS
 * 
 * This file is part of Mconf-Mobile.
 *
 * Mconf-Mobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Mconf-Mobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Mconf-Mobile.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mconf.bbb.android;

import org.mconf.bbb.BigBlueButtonClient;
import org.mconf.bbb.IBigBlueButtonClientListener;
import org.mconf.bbb.android.voip.VoiceModule;
import org.mconf.bbb.chat.ChatMessage;
import org.mconf.bbb.listeners.IListener;
import org.mconf.bbb.listeners.Listener;
import org.mconf.bbb.users.IParticipant;
import org.sipdroid.sipua.ui.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SlidingDrawer;
import android.widget.SlidingDrawer.OnDrawerCloseListener;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SlidingDrawer.OnDrawerOpenListener;


public class Client extends Activity implements IBigBlueButtonClientListener {
	private static final Logger log = LoggerFactory.getLogger(Client.class);

	public static final int MENU_QUIT = Menu.FIRST;
	public static final int MENU_LOGOUT = Menu.FIRST + 1;
	public static final int MENU_RAISE_HAND = Menu.FIRST + 2;
	public static final int MENU_START_VOICE = Menu.FIRST + 3;
	public static final int MENU_STOP_VOICE = Menu.FIRST + 4;
	public static final int MENU_MUTE = Menu.FIRST + 5; 
	public static final int MENU_SPEAKER = Menu.FIRST + 6;
	public static final int MENU_AUDIO_CONFIG = Menu.FIRST + 7;
	public static final int MENU_ABOUT = Menu.FIRST + 8;

	public static final int CHAT_NOTIFICATION_ID = 77000;

	public static final String ACTION_OPEN_SLIDER = "org.mconf.bbb.android.Client.OPEN_SLIDER";
	private static final String FINISH = "bbb.android.action.FINISH";

	private static final String SEND_TO_BACK = "bbb.android.action.SEND_TO_BACK";

	public static final int KICK_USER = Menu.FIRST;
	public static final int SET_PRESENTER = Menu.FIRST+2;

	public static final int ROW_HEIGHT = 42;

	private static final int KICK_LISTENER = Menu.FIRST+3;
	private static final int MUTE_LISTENER = Menu.FIRST+1;
	
	private static boolean firstTime=true;



	public boolean isFirstTime() {
		return firstTime;
	}

	public void setFirstTime(boolean firstTime) {
		firstTime = firstTime;
	}


	BroadcastReceiver chatClosed = new BroadcastReceiver(){ 
		public void onReceive(Context context, Intent intent)
		{ 
			Bundle extras = intent.getExtras();
			int userId= extras.getInt("userId");
			if(chatAdapter.hasUser(userId))
				contactAdapter.setChatStatus(userId, Contact.CONTACT_ON_PUBLIC_MESSAGE);
			else
				contactAdapter.setChatStatus(userId, Contact.CONTACT_NORMAL);

			contactAdapter.notifyDataSetChanged();

		} 
	};





	public static BigBlueButtonClient bbb = new BigBlueButtonClient();
	protected ContactAdapter contactAdapter;
	protected ChatAdapter chatAdapter;
	protected ListenerAdapter listenerAdapter;

	protected String myusername;
	protected SlidingDrawer slidingDrawer;
	protected Button slideHandleButton;

	protected SlidingDrawer listenersDrawer;
	protected Button listenersHandleButton;


	protected boolean loggedIn=true;

	private VoiceModule voice;

	private CustomListview contactListView;
	CustomListview listenerListView;




	//	protected ClientBroadcastReceiver receiver = new ClientBroadcastReceiver();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		log.debug("onCreate");
		int orientation = getResources().getConfiguration().orientation;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		if(orientation==Configuration.ORIENTATION_PORTRAIT)
			setContentView(R.layout.contacts_list);   
		else 
		{
			setContentView(R.layout.contacts_list_landscape);  
			int width = getResources().getDisplayMetrics().widthPixels;
			LayoutParams params = findViewById(R.id.frame3).getLayoutParams();
			params.width=(width/2)-1;
			findViewById(R.id.frame3).setLayoutParams(params);
			params = findViewById(R.id.frame4).getLayoutParams();
			params.width=(width/2)-1;
			findViewById(R.id.frame4).setLayoutParams(params);
			
		}
			
		slidingDrawer = (SlidingDrawer) findViewById(R.id.slide);
		slideHandleButton = (Button) findViewById(R.id.handle);
		if(orientation==Configuration.ORIENTATION_LANDSCAPE)
		{
			slidingDrawer.open();
			slidingDrawer.lock();
		}

		ScrollView scrollView = (ScrollView)findViewById(R.id.Scroll);
		// Hide the Scollbar
		scrollView.setVerticalScrollBarEnabled(false);



		slidingDrawer.setOnDrawerOpenListener(new OnDrawerOpenListener() {

			@Override
			public void onDrawerOpened() {


				NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				notificationManager.cancel(CHAT_NOTIFICATION_ID);
				Button handler = (Button)findViewById(R.id.handle);
				handler.setBackgroundDrawable(getApplicationContext().getResources().getDrawable(R.drawable.public_chat_title_background));
				handler.setGravity(Gravity.CENTER);
				openedDrawer(); 
			}
		});




		slidingDrawer.setOnDrawerCloseListener(new OnDrawerCloseListener() {

			@Override
			public void onDrawerClosed() {


			}
		});


		if(isFirstTime())
		{
			Bundle extras = getIntent().getExtras();
			myusername = extras.getString("username");
			Toast.makeText(getApplicationContext(),getResources().getString(R.string.welcome) + ", " + myusername, Toast.LENGTH_SHORT).show(); 
			setFirstTime(false);
		}
		setFirstTime(false);
		chatAdapter = new ChatAdapter(this);
		final ListView chatListView = (ListView)findViewById(R.id.messages);
		chatListView.setAdapter(chatAdapter);

		contactListView = (CustomListview)findViewById(R.id.contacts_list); 
		contactAdapter = new ContactAdapter(this);
		contactListView.setAdapter(contactAdapter);
		registerForContextMenu(contactListView);



		listenerListView = (CustomListview)findViewById(R.id.listeners_list);
		listenerAdapter = new ListenerAdapter(this);
		listenerListView.setAdapter(listenerAdapter);
		registerForContextMenu(listenerListView);




		Button send = (Button)findViewById(R.id.sendMessage);
		send.setOnClickListener( new OnClickListener() {
			@Override
			public void onClick(View viewParam) {
				EditText chatMessageEdit = (EditText) findViewById(R.id.chatMessage);
				String chatMessage = chatMessageEdit.getText().toString();
				if(chatMessage.length()>1)
				{
					bbb.sendPublicChatMessage(chatMessage);
					chatMessageEdit.setText("");
				}
				chatListView.setSelection(chatListView.getCount());
			}
		});


		contactListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				final Contact contact = (Contact) contactAdapter.getItem(position); 

				//se o ID da pessoa clicada for diferente do meu ID
				if (contact.getUserId() != bbb.getMyUserId())
					startPrivateChat(contact);
			}
		});

	
		Receiver.mContext = this;
		voice = new VoiceModule(this,
				bbb.getJoinService().getJoinedMeeting().getFullname(), 
				bbb.getJoinService().getServerUrl());
		bbb.addListener(this);
		bbb.connectBigBlueButton();


		IntentFilter filter = new IntentFilter(PrivateChat.CHAT_CLOSED); 
		registerReceiver(chatClosed, filter); 
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		if (bbb.getUsersModule().getParticipants().get(bbb.getMyUserId()).isModerator()) {
			if(v.getId()==R.id.contacts_list)
			{
				final Contact contact = (Contact) contactAdapter.getItem(info.position);

				if (contact.getUserId() != bbb.getMyUserId()) {
					menu.add(0, KICK_USER, 0, R.string.kick);
				}

				if (!contact.isPresenter())
					menu.add(0, SET_PRESENTER, 0, R.string.assign_presenter);

			}
			else
			{
				final Listener listener = (Listener) listenerAdapter.getItem(info.position);
				menu.add(0, KICK_LISTENER, 0, R.string.kick);
				int muted;
				if(listener.isMuted())
					muted=R.string.unmute;
				else
					muted = R.string.mute;
				menu.add(0, MUTE_LISTENER, 0, muted);
			}
		}

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
		Contact contact = null;
		Listener listener = null;
		if(item.getItemId()==KICK_USER||item.getItemId()==SET_PRESENTER)
		{
			contact= (Contact) contactAdapter.getItem(info.position);
			log.debug("clicked on participant " + contact.toString());
		}
		else
		{
			listener=(Listener) listenerAdapter.getItem(info.position);
			log.debug("clicked on listener " + listener.toString());
		}
		switch (item.getItemId()) {
		case KICK_USER:
			bbb.kickUser(contact.getUserId());
			Intent kickedUser = new Intent(PrivateChat.KICKED_USER);
			kickedUser.putExtra("userId", contact.getUserId());
			sendBroadcast(kickedUser);
			return true;
		case SET_PRESENTER:
			bbb.assignPresenter(contact.getUserId());
			return true;
		case MUTE_LISTENER:
			bbb.muteUnmuteListener(listener.getUserId(), !listener.isMuted());
			return true;
		case KICK_LISTENER: 
			bbb.kickListener(listener.getUserId());
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	protected void onDestroy() {
		log.debug("onDestroy");

		quit();

		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancelAll();	

		unregisterReceiver(chatClosed);

		super.onDestroy();
	}

	//	private class ClientBroadcastReceiver extends BroadcastReceiver {
	//
	//		@Override
	//		public void onReceive(Context context, Intent intent) {
	//			if (intent.getAction().equals(ACTION_OPEN_SLIDER)) {
	//				if (!slidingDrawer.isOpened())
	//					slidingDrawer.open();
	//			}
	//		}
	//		
	//	}

	private void quit() {
		if (voice.isOnCall())
			voice.hang();
		bbb.removeListener(this);
		bbb.disconnect();
	}

	private void startPrivateChat(final Contact contact) {

		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				contactAdapter.setChatStatus(contact.getUserId(), Contact.CONTACT_ON_PRIVATE_MESSAGE); 
				contactAdapter.notifyDataSetChanged();
			}

		});

		Intent intent = new Intent(getApplicationContext(), PrivateChat.class);
		intent.putExtra("username", contact.getName());
		intent.putExtra("userId", contact.getUserId());
		intent.putExtra("notified", false);
		startActivity(intent);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (voice.isOnCall()) {
			if (voice.isMuted())
				menu.add(Menu.NONE, MENU_MUTE, Menu.NONE, R.string.unmute).setIcon(android.R.drawable.ic_lock_silent_mode_off);
			else
				menu.add(Menu.NONE, MENU_MUTE, Menu.NONE, R.string.mute).setIcon(android.R.drawable.ic_lock_silent_mode);
			if (voice.getSpeaker() == AudioManager.MODE_NORMAL)
				menu.add(Menu.NONE, MENU_SPEAKER, Menu.NONE, R.string.speaker).setIcon(android.R.drawable.button_onoff_indicator_on);
			else
				menu.add(Menu.NONE, MENU_SPEAKER, Menu.NONE, R.string.speaker).setIcon(android.R.drawable.button_onoff_indicator_off);
			menu.add(Menu.NONE, MENU_AUDIO_CONFIG, Menu.NONE, R.string.audio_config).setIcon(android.R.drawable.ic_menu_preferences);
			menu.add(Menu.NONE, MENU_STOP_VOICE, Menu.NONE, R.string.stop_voice).setIcon(android.R.drawable.ic_btn_speak_now);
		} else {
			menu.add(Menu.NONE, MENU_START_VOICE, Menu.NONE, R.string.start_voice).setIcon(android.R.drawable.ic_btn_speak_now);
		}
		menu.add(Menu.NONE, MENU_RAISE_HAND, Menu.NONE, R.string.raise_hand).setIcon(android.R.drawable.ic_menu_myplaces);
		menu.add(Menu.NONE, MENU_LOGOUT, Menu.NONE, R.string.logout).setIcon(android.R.drawable.ic_menu_revert);
		menu.add(Menu.NONE, MENU_QUIT, Menu.NONE, R.string.quit).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about).setIcon(android.R.drawable.ic_menu_info_details);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent= new Intent(FINISH);
		switch (item.getItemId()) {
		case MENU_START_VOICE:
			voice.call(bbb.getJoinService().getJoinedMeeting().getVoicebridge());
			return true;

		case MENU_STOP_VOICE:
			voice.hang();
			return true;

		case MENU_MUTE:
			voice.muteCall(!voice.isMuted());
			return true;

		case MENU_SPEAKER:
			voice.setSpeaker(voice.getSpeaker() != AudioManager.MODE_NORMAL? AudioManager.MODE_NORMAL: AudioManager.MODE_IN_CALL);
			return true;

		case MENU_LOGOUT:
			quit();
			Intent login = new Intent(this, LoginPage.class);
			login.putExtra("username", myusername);
			startActivity(login);
			sendBroadcast(intent);
			finish();
			return true;

		case MENU_QUIT:
			quit();
			sendBroadcast(intent);
			finish();
			return true;

		case MENU_RAISE_HAND:
			if (bbb.getUsersModule().getParticipants().get(bbb.getMyUserId()).isRaiseHand())
				bbb.raiseHand(false);
			else
				bbb.raiseHand(true);
			return true;

		case MENU_ABOUT:
			new AboutDialog(this).show();
			return true;

		case MENU_AUDIO_CONFIG:
			new AudioControlDialog(this).show();
			return true;
		default:			
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Intent intent = new Intent(SEND_TO_BACK);
			sendBroadcast(intent);
			log.debug("KEYCODE_BACK");
			moveTaskToBack(true);
			return true;
			//    		case KeyEvent.KEYCODE_VOLUME_DOWN:
			//    		case KeyEvent.KEYCODE_VOLUME_UP:
			//				Dialog dialog = new AudioControlDialog(this);
			//				dialog.show();
			//				return true;
		default:
			return super.onKeyDown(keyCode, event);
		}    		
	}

	@Override
	public void onConnected() {
		// TODO Auto-generated method stub

	}
	@Override
	public void onDisconnected() {

	}
	@Override
	public void onKickUserCallback() {
		// TODO Auto-generated method stub

	}
	@Override
	public void onParticipantJoined(final IParticipant p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				contactAdapter.addSection(p);
				contactAdapter.sort();

				contactAdapter.notifyDataSetChanged();

				setListHeight(contactListView);


			}
		});		
	}
	@Override
	public void onParticipantLeft(final IParticipant p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				contactAdapter.removeSection(p);
				contactAdapter.sort();

				contactAdapter.notifyDataSetChanged();
				setListHeight(contactListView);

			}
		});
	}

	@Override
	public void onPrivateChatMessage(ChatMessage message, IParticipant source) {

		// if the message received was sent from me, don't show any notification
		if (message.getUserId() == bbb.getMyUserId())
			return;

		showNotification(message, source, true);

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		log.debug("onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);
		super.onActivityResult(requestCode, resultCode, data);		
	}

	@Override
	protected void onNewIntent(Intent intent) {
		log.debug("onNewIntent");
		super.onNewIntent(intent);

		if (intent.getAction() == null)
			return;
		else if (intent.getAction().equals(ACTION_OPEN_SLIDER)) {
			if (!slidingDrawer.isOpened())
			{
				slidingDrawer.open();
				openedDrawer();
				Button handler = (Button)findViewById(R.id.handle);
				handler.setBackgroundDrawable(this.getApplicationContext().getResources().getDrawable(R.drawable.public_chat_title_background));
			}

		}
	}

	public void showNotification(final ChatMessage message, IParticipant source, final boolean privateChat) {
		// remember that source could be null! that happens when a user send a message and log out - the list of participants don't have the entry anymore

		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				// change the background color of the message source

				contactAdapter.setChatStatus(message.getUserId(), privateChat? Contact.CONTACT_ON_PRIVATE_MESSAGE: Contact.CONTACT_ON_PUBLIC_MESSAGE);
				contactAdapter.sort();
				contactAdapter.notifyDataSetChanged();
			}
		});		

		String contentTitle = getResources().getString(privateChat? R.string.private_chat_notification: R.string.public_chat_notification) + message.getUsername();

		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		Notification notification = new Notification(R.drawable.icon_bbb, contentTitle, System.currentTimeMillis());

		Intent notificationIntent = null;
		if (privateChat) {
			Contact contact = contactAdapter.getUserById(source.getUserId());			

			notificationIntent = new Intent(getApplicationContext(), PrivateChat.class);
			notificationIntent.putExtra("username", contact.getName());
			notificationIntent.putExtra("userId", contact.getUserId());
			notificationIntent.putExtra("notified", true);
			/**
			 *  http://groups.google.com/group/android-developers/browse_thread/thread/e61ec1e8d88ea94d
			 */
			notificationIntent.setData(Uri.parse("custom://"+SystemClock.elapsedRealtime())); 

			PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(),0, notificationIntent,Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
			notification.setLatestEventInfo(getApplicationContext(), contentTitle, message.getMessage(), contentIntent);
			notificationManager.notify(CHAT_NOTIFICATION_ID + message.getUserId(), notification);
		} else {
			notificationIntent = new Intent(getApplicationContext(), Client.class);
			notificationIntent.setAction(ACTION_OPEN_SLIDER);

			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
			notification.setLatestEventInfo(getApplicationContext(), contentTitle, message.getMessage(), contentIntent);
			notificationManager.notify(CHAT_NOTIFICATION_ID, notification);	
		}
	}

	public void dismissNotification(int userId) {

	}

	@Override
	public void onPublicChatMessage(final ChatMessage message, final IParticipant source) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				chatAdapter.add(message);
				chatAdapter.notifyDataSetChanged();
				if (!slidingDrawer.isShown() || !slidingDrawer.isOpened())
				{
					showNotification(message, source, false);
					Button handler = (Button)findViewById(R.id.handle);
					handler.setBackgroundDrawable(getApplicationContext().getResources().getDrawable(R.drawable.public_chat_title_background_new_message)); 
				}
			}
		});


	}

	@Override
	public void onParticipantStatusChangePresenter(final IParticipant p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				contactAdapter.getUserById(p.getUserId()).getStatus().setPresenter(p.getStatus().isPresenter());
				contactAdapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void onParticipantStatusChangeHasStream(final IParticipant p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				contactAdapter.getUserById(p.getUserId()).getStatus().setHasStream(p.getStatus().isHasStream());
				contactAdapter.getUserById(p.getUserId()).getStatus().setStreamName(p.getStatus().getStreamName());
				contactAdapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void onParticipantStatusChangeRaiseHand(final IParticipant p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				contactAdapter.getUserById(p.getUserId()).getStatus().setRaiseHand(p.getStatus().isRaiseHand());
				contactAdapter.notifyDataSetChanged();
			}
		});
	}

	public boolean isLoggedIn()
	{
		return loggedIn;
	}

	public void setLoggedIn(boolean state)
	{
		loggedIn=state;
	}

	public void openedDrawer()
	{
		int position;
		for(position = 0; position<contactAdapter.getCount(); position++)
		{
			if(contactAdapter.getChatStatus(position)==Contact.CONTACT_ON_PUBLIC_MESSAGE)
			{
				if(PrivateChat.hasUserOnPrivateChat(contactAdapter.getUserId(position)))
					contactAdapter.setChatStatus(contactAdapter.getUserId(position), Contact.CONTACT_ON_PRIVATE_MESSAGE);
				else
					contactAdapter.setChatStatus(contactAdapter.getUserId(position), Contact.CONTACT_NORMAL);
			}
		}
		contactAdapter.sort();
		contactAdapter.notifyDataSetChanged();
	}

	@Override
	public void onListenerJoined(final IListener p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				listenerAdapter.addSection(p);

				listenerAdapter.notifyDataSetChanged();
				setListHeight(listenerListView);
			}
		});		
	}
	@Override
	public void onListenerLeft(final IListener p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				listenerAdapter.removeSection(p);

				listenerAdapter.notifyDataSetChanged();		
				setListHeight(listenerListView);
			}
		});
	}

	@Override
	public void onListenerStatusChangeIsTalking(final IListener p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				listenerAdapter.getUserById(p.getUserId()).setTalking(p.isTalking());
				listenerAdapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void onListenerStatusChangeIsMuted(final IListener p) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				listenerAdapter.getUserById(p.getUserId()).setMuted(p.isMuted());
				listenerAdapter.notifyDataSetChanged();
			}
		});
	}


	public void setListHeight(CustomListview listView) {

		int totalHeight = 0;
		Resources r = getResources();
		int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ROW_HEIGHT, r.getDisplayMetrics());
		totalHeight= listView.getCount()*(px+1);
		ViewGroup.LayoutParams params = listView.getLayoutParams();
		params.height = totalHeight + (listView.getDividerHeight() * (listView.getCount() - 1));
		listView.setLayoutParams(params); 
		listView.requestLayout();
	} 

	/*@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);
	  if(newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE)
		  setContentView(R.layout.contacts_list_landscape);
	  else
		  setContentView(R.layout.contacts_list);
	}*/




}


