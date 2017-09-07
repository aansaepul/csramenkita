package com.aansanwar.csramenkita;

import android.app.DialogFragment;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;
import com.ibm.watson.developer_cloud.conversation.v1.ConversationService;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageRequest;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageResponse;
import com.ibm.watson.developer_cloud.service.exception.BadRequestException;
import com.ibm.watson.developer_cloud.service.exception.UnauthorizedException;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String USER_USER = "user";
    private static final String USER_WATSON = "watson";

    private ConversationService conversationService;
    private Map<String, Object> conversationContext;
    private ArrayList<ConversationMessage> conversationLog;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        conversationService = new ConversationService(ConversationService.VERSION_DATE_2016_07_11);
        conversationService.setUsernameAndPassword(getString(R.string.watson_conversation_username),
                getString(R.string.watson_conversation_password));
        conversationLog = new ArrayList<>();

        // If we have a savedInstanceState, recover the previous Context and Message Log.
        if (savedInstanceState != null) {
            conversationContext = (Map<String, Object>) savedInstanceState.getSerializable("context");
            conversationLog = (ArrayList<ConversationMessage>) savedInstanceState.getSerializable("backlog");

            // Repopulate the UI with the previous Messages.
            if (conversationLog != null) {
                for (ConversationMessage message : conversationLog) {
                    addMessageFromUser(message);
                }
            }

            final ScrollView scrollView = (ScrollView) findViewById(R.id.message_scrollview);
            scrollView.scrollTo(0, scrollView.getBottom());
        } else {
            // Validate that the user's credentials are valid and that we can continue.
            // This also kicks off the first Conversation Task to obtain the intro message from Watson.
            ValidateCredentialsTask vct = new ValidateCredentialsTask();
            vct.execute();
        }

        ImageButton sendButton = (ImageButton) findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView entryText = (TextView) findViewById(R.id.entry_text);
                String text = entryText.getText().toString();

                if (!text.isEmpty()) {
                    // Add the message to the UI.
                    addMessageFromUser(new ConversationMessage(text, USER_USER));

                    // Record the message in the conversation log.
                    conversationLog.add(new ConversationMessage(text, USER_USER));

                    // Send the message to Watson Conversation.
                    ConversationTask ct = new ConversationTask();
                    ct.execute(text);

                    entryText.setText("");
                }

            }
        });

        // Core SDK must be initialized to interact with Bluemix Mobile services.
        BMSClient.getInstance().initialize(getApplicationContext(), BMSClient.REGION_US_SOUTH);

    }

    @Override
    public void onResume() {
        super.onResume();


    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
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
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.clear_session_action) {

            LinearLayout messageContainer = (LinearLayout) findViewById(R.id.message_container);
            messageContainer.removeAllViews();

            ConversationTask ct = new ConversationTask();
            ct.execute("");
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        // Retain the conversation context and the log of previous messages, if they exist.
        if (conversationContext != null) {
            HashMap map = new HashMap(conversationContext);
            savedInstanceState.putSerializable("context", map);
        }
        if (conversationLog != null) {
            savedInstanceState.putSerializable("backlog", conversationLog);
        }
    }

    /**
     * Displays an AlertDialogFragment with the given parameters.
     *
     * @param errorTitle   Error Title from values/strings.xml.
     * @param errorMessage Error Message either from values/strings.xml or response from server.
     * @param canContinue  Whether the application can continue without needing to be rebuilt.
     */
    private void showDialog(int errorTitle, String errorMessage, boolean canContinue) {
        DialogFragment newFragment = AlertDialogFragment.newInstance(errorTitle, errorMessage, canContinue);
        newFragment.show(getFragmentManager(), "dialog");
    }

    /**
     * Adds a message dialog view to the UI.
     *
     * @param message ConversationMessage containing a message and the sender.
     */
    private void addMessageFromUser(ConversationMessage message) {
        View messageView;
        LinearLayout messageContainer = (LinearLayout) findViewById(R.id.message_container);

        if (message.getUser().equals(USER_WATSON)) {
            messageView = this.getLayoutInflater().inflate(R.layout.watson_text, messageContainer, false);
            TextView watsonMessageText = (TextView) messageView.findViewById(R.id.watsonTextView);
            watsonMessageText.setText(message.getMessageText());
        } else {
            messageView = this.getLayoutInflater().inflate(R.layout.user_text, messageContainer, false);
            TextView userMessageText = (TextView) messageView.findViewById(R.id.userTextView);
            userMessageText.setText(message.getMessageText());
        }

        messageContainer.addView(messageView);

        // Scroll to the bottom of the view so the user sees the update.
        final ScrollView scrollView = (ScrollView) findViewById(R.id.message_scrollview);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    /**
     * Asynchronously contacts the Watson Conversation Service to see if provided Credentials are valid.
     */
    private class ValidateCredentialsTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {

            // Mark whether or not the validation completes.
            boolean success = true;

            try {
                conversationService.getToken().execute();
            } catch (Exception ex) {

                success = false;

                // See if the user's credentials are valid or not, along with other errors.
                if (ex.getClass().equals(UnauthorizedException.class) ||
                        ex.getClass().equals(IllegalArgumentException.class)) {
                    showDialog(R.string.error_title_invalid_credentials,
                            getString(R.string.error_message_invalid_credentials), false);
                } else if (ex.getCause() != null &&
                        ex.getCause().getClass().equals(UnknownHostException.class)) {
                    showDialog(R.string.error_title_bluemix_connection,
                            getString(R.string.error_message_bluemix_connection), true);
                } else {
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                }
            }

            return success;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            // If validation succeeded, then get the opening message from Watson Conversation
            // by sending an empty input string to the ConversationTask.
            if (success) {
                ConversationTask ct = new ConversationTask();
                ct.execute("");
            }
        }
    }

    /**
     * Asynchronously sends the user's message to Watson Conversation and receives Watson's response.
     */
    private class ConversationTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String entryText = params[0];

            MessageRequest messageRequest;

            // Send Context to Watson in order to continue a conversation.
            if (conversationContext == null) {
                messageRequest = new MessageRequest.Builder()
                        .inputText(entryText).build();
            } else {
                messageRequest = new MessageRequest.Builder()
                        .inputText(entryText)
                        .context(conversationContext).build();
            }

            try {
                // Send the message to the workspace designated in watson_credentials.xml.
                MessageResponse messageResponse = conversationService.message(
                        getString(R.string.watson_conversation_workspace_id), messageRequest).execute();

                conversationContext = messageResponse.getContext();
                return messageResponse.getText().get(0);
            } catch (Exception ex) {
                // A failure here is usually caused by an incorrect workspace in watson_credentials.
                if (ex.getClass().equals(BadRequestException.class)) {
                    showDialog(R.string.error_title_invalid_workspace,
                            getString(R.string.error_message_invalid_workspace), false);
                } else {
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                }
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            // Add the message from Watson to the UI.
            addMessageFromUser(new ConversationMessage(result, USER_WATSON));

            // Record the message from Watson in the conversation log.
            conversationLog.add(new ConversationMessage(result, USER_WATSON));
        }
    }


    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_facebook) {
            startActivity(new Intent(MainActivity.this,ViewFbActivity.class));
            // Handle the camera action
        } else if (id == R.id.nav_Instagram) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/ramenkita/")));
        } else if (id == R.id.nav_map) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://goo.gl/maps/snErFx1uPAo")));
        } else if (id == R.id.nav_wa) {
            startActivity(new Intent(getPackageManager().getLaunchIntentForPackage("com.whatsapp")));
        } else if (id == R.id.nav_telepon) {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:(+62)8112209040")));
        } else if (id == R.id.nav_gojek) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.go-food.co.id/")));
        } else if (id == R.id.nav_ttg) {
            startActivity(new Intent(MainActivity.this, AboutActivity.class));

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
