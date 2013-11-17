package org.codesoup.notifymytweets;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class LoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
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

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState == null) {
                SharedPreferences preferences = getActivity().getSharedPreferences("NotifyMyTweets", MODE_PRIVATE);

                TextView loginEdit = (TextView) getView().findViewById(R.id.loginEdit);
                TextView passwordEdit = (TextView) getView().findViewById(R.id.passwordEdit);

                CharSequence login = preferences.getString("login", null);
                CharSequence password = preferences.getString("password", null);

                if (login != null)
                    loginEdit.setText(login);
                else
                    loginEdit.setText("lol");

                if (password != null)
                    passwordEdit.setText(password);
            }
        }


        @Override
        public void onStop() {
            SharedPreferences preferences = getActivity().getSharedPreferences("NotifyMyTweets", MODE_PRIVATE);
            TextView loginEdit = (TextView) getView().findViewById(R.id.loginEdit);
            TextView passwordEdit = (TextView) getView().findViewById(R.id.passwordEdit);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("login", String.valueOf(loginEdit.getText()));
            editor.putString("password", String.valueOf(passwordEdit.getText()));
            editor.commit();
            super.onStop();
        }
    }
}
