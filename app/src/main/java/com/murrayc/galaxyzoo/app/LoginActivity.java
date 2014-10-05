package com.murrayc.galaxyzoo.app;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * A login screen that offers login via username/password.
 */
public class LoginActivity extends AccountAuthenticatorActivity {

    /** The Intent extra to store username. */
    public static final String ARG_USERNAME = "username";

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    // UI references.
    private EditText mUsernameView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private boolean mRequestNewAccount = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        // Set up the login form.
        mUsernameView = (EditText) findViewById(R.id.username);

        //Get the name that was succeeded last time, if any:
        String authName = null;
        final Intent intent = getIntent();
        if (intent != null) {
            authName = intent.getStringExtra(ARG_USERNAME);
        }

        mRequestNewAccount = TextUtils.isEmpty(authName);

        mUsernameView.setText(authName);

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        //This voodoo makes the textviews' HTML links clickable:
        //See http://stackoverflow.com/questions/2734270/how-do-i-make-links-in-a-textview-clickable/20647011#20647011
        TextView textView = (TextView) findViewById(R.id.textViewForgot);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView = (TextView) findViewById(R.id.textViewRegister);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        Button mUsernameSignInButton = (Button) findViewById(R.id.username_sign_in_button);
        mUsernameSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);

        // Show the Up button in the action bar.
        final ActionBar actionBar = getActionBar();
        if (actionBar == null)
            return;

        actionBar.setDisplayHomeAsUpEnabled(true);

        //Get the existing logged-in username, if any:
        final LoginUtils.GetExistingLogin task = new LoginUtils.GetExistingLogin(this) {
            @Override
            protected void onPostExecute(final LoginUtils.LoginDetails loginDetails) {
                super.onPostExecute(loginDetails);

                onExistingLoginRetrieved(loginDetails);
            }
        };
        task.execute();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            // This activity has no single possible parent activity.
            // In this case Up should be the same as Back.
            // See "Navigating to screens with multiple entry points":
            //   http://developer.android.com/design/patterns/navigation.html
            // Just closing the activity might be enough:
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void onExistingLoginRetrieved(final LoginUtils.LoginDetails loginDetails) {

        String authName = null;
        if (loginDetails == null) {
            Log.error("uploadOutstandingClassifications(): getAccountLoginDetails() returned null");
        } else {
            authName = loginDetails.name;
        }

        mUsernameView.setText(authName);
        mRequestNewAccount = TextUtils.isEmpty(authName);
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String username = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;


        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid username:
        if (TextUtils.isEmpty(username)) {
            mUsernameView.setError(getString(R.string.error_field_required));
            focusView = mUsernameView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(username, password);
            mAuthTask.execute((Void) null);
        }
    }

    private boolean isPasswordValid(String password) {
        //TODO: Replace this with your own logic
        return password.length() > 4;
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void finishWithResult(final LoginUtils.LoginResult result) {
        boolean loggedIn = false;
        if ((result != null) && result.getSuccess()) {
            loggedIn = true;
        }

        if(loggedIn) {
            UiUtils.showLoggedInToast(this);
        }

        if (loggedIn) {
            final AccountManager accountManager = AccountManager.get(this);
            final Account account = new Account(result.getName(), LoginUtils.ACCOUNT_TYPE);
            if (mRequestNewAccount) {
                accountManager.addAccountExplicitly(account, null, null);
            }

            //TODO? ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)

            //This is apparently not necessary, when updating an existing account,
            //if this activity was launched from our Authenticator, for instance if our
            //Authenticator found that the accounts' existing auth token was invalid.
            //Presumably it is necessary if this activity is launched from our app.
            accountManager.setAuthToken(account, LoginUtils.ACCOUNT_AUTHTOKEN_TYPE, result.getApiKey());
        }

        final Intent intent = new Intent();
        if (loggedIn) {
            intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, result.getName());
            intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, LoginUtils.ACCOUNT_TYPE);
        }

        //This sets the AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE response,
        //for when this activity was launched by our Authenticator.
        setAccountAuthenticatorResult(intent.getExtras());

        setResult(loggedIn ? RESULT_OK : RESULT_CANCELED, intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // super.onBackPressed();

        //Let callers (via startActivityForResult() know that this was cancelled.
        finishWithResult(null);
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, LoginUtils.LoginResult> {

        private final String mUsername;
        private final String mPassword;

        UserLoginTask(String username, String password) {
            mUsername = username;
            mPassword = password;
        }

        @Override
        protected LoginUtils.LoginResult doInBackground(Void... params) {
            final ContentResolver contentResolver = getContentResolver();
            if (contentResolver == null) {
                return null;
            }

            return LoginUtils.loginSync(mUsername, mPassword);
        }

        @Override
        protected void onPostExecute(final LoginUtils.LoginResult result) {
            mAuthTask = null;
            showProgress(false);

            if ((result != null) && result.getSuccess()) {
                finishWithResult(result);
            } else {
                if (!Utils.getNetworkIsConnected(LoginActivity.this)) {
                    UiUtils.warnAboutNoNetworkConnection(LoginActivity.this);
                    return;
                }

                mPasswordView.setError(getString(R.string.error_incorrect_password));
                mPasswordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}



