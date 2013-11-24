package org.codesoup.notifymytweets;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import twitter4j.TwitterException;

import static org.codesoup.notifymytweets.Secrets.CONSUMER_KEY;
import static org.codesoup.notifymytweets.Secrets.CONSUMER_SECRET;

public class LoginActivity extends Activity implements Handler.Callback {
    public static final String TAG = "LoginActivity";

    private HandlerThread mHandlerThread;
    private Handler mainHandler;
    private Handler threadHandler;

    static String CALLBACK = "notifymytweets://oauth/";

    private TwitterAuthentication mTwitter;

    private ServiceConnection mConnection;
    private NotifierService serviceRef;

    public static final int SHOW_TOAST = 1;
    public static final int OAUTH_REQUEST = 2;
    public static final int OAUTH_ACCESS = 3;
    public static final int START_WEBVIEW = 4;

    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case SHOW_TOAST:
                Context context = LoginActivity.this.getApplicationContext();
                Toast.makeText(context, (CharSequence) msg.obj, Toast.LENGTH_LONG).show();
                return true;
            case OAUTH_ACCESS:
                OAuthAccess((String) msg.obj);
                return true;
            case OAUTH_REQUEST:
                if (!mTwitter.isAuthorized()) {
                    OAuthRequest();
                }
                return true;
            case START_WEBVIEW:
                startWebView(String.valueOf(msg.obj));
                return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }

        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("Network");
            mHandlerThread.start();
            threadHandler = new Handler(mHandlerThread.getLooper(), this);
        }

        if (mainHandler == null) {
            mainHandler = new Handler(this);
        }

        SharedPreferences preferences = getSharedPreferences("NotifyMyTweets", MODE_PRIVATE);
        String serializedTwitter = preferences.getString("twitter", null);

        if (serializedTwitter != null) {
            try {
                byte b[] = Base64.decode(serializedTwitter, 0);
                ByteArrayInputStream bi = new ByteArrayInputStream(b);
                ObjectInputStream si = new ObjectInputStream(bi);
                mTwitter = (TwitterAuthentication) si.readObject();
            } catch (Exception ex) {
                asyncToast("Could not load saved credentials");
                Log.e(TAG, "Could not load saved credentials", ex);
            }
        }

        // serialized state absent or invalid
        if (mTwitter == null) {
            mTwitter = new TwitterWrapper(CONSUMER_KEY, CONSUMER_SECRET);
            mTwitter.setCallbackUri(CALLBACK);
        }
        // next step comes in onResume
    }

    @Override
    protected void onResume() {
        super.onResume();
        startNotifierService();
        Message.obtain(threadHandler, OAUTH_REQUEST).sendToTarget();
    }

    @Override
    protected void onStop() {
        unbindService(mConnection);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        SharedPreferences preferences = getSharedPreferences("NotifyMyTweets", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream so = new ObjectOutputStream(bo);
            so.writeObject(mTwitter);
            so.flush();

            editor.putString("twitter", Base64.encodeToString(bo.toByteArray(), 0));
        } catch (Exception ex) {
            asyncToast("Could not save credentials");
            Log.e(TAG, "Could not save credentials", ex);
        }
        editor.commit();
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        mainHandler = null;
        threadHandler = null;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void asyncToast(String message) {
        Message.obtain(mainHandler, SHOW_TOAST, message).sendToTarget();
    }

    public void startWebView(String url) {
        WebView webview = new WebView(this);
        webview.setWebViewClient(new WebViewClient() {
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Uri uri = Uri.parse(url);
                if (uri.getScheme().equals("notifymytweets") && uri.getHost().equals("oauth")) {
                    String denied = uri.getQueryParameter("denied");
                    String oauth_token = uri.getQueryParameter("oauth_token");
                    final String oauth_verifier = uri.getQueryParameter("oauth_verifier");
                    if (denied != null) {
                        asyncToast("You denied app authorization");
                    } else if (oauth_token != null && oauth_verifier != null) {
                        Message.obtain(threadHandler, OAUTH_ACCESS, oauth_verifier).sendToTarget();
                    } else {
                        asyncToast("Wrong callback parameters");
                    }
                    setContentView(R.layout.activity_main);
                }
            }
        });

        setContentView(webview);
        webview.loadUrl(url);
    }
    public void OAuthRequest() {
        try {
            Message.obtain(mainHandler, START_WEBVIEW, mTwitter.getAuthorizationUrl()).sendToTarget();
        } catch (TwitterException ex) {
            asyncToast("Could not login to twitter");
            Log.e(TAG, "Could not login to twitter", ex);
        }
    }

    public void OAuthAccess(String oauth_verifier) {
        try {
            mTwitter.setOAuthVerifier(oauth_verifier);
            mTwitter.getOAuthAccessToken();
            asyncToast("Notifier is now active");
            // ready ! now we can start fetching messages
        } catch (TwitterException ex) {
            asyncToast("Invalid authorization request token");
            Log.e(TAG, "Invalid authorization request token", ex);
        }
    }

    private void startNotifierService() {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder service) {
                serviceRef = ((NotifierService.NotifierBinder) service).getService();
                mTwitter.setNewTweetListener(serviceRef);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                serviceRef = null;
            }
        };
        Intent bindIntent = new Intent(this, NotifierService.class);
        bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);

        Intent intent = new Intent(this, NotifierService.class);
        startService(intent);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }
}