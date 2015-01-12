package com.yeleman.ureportcontactupload;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TableLayout;
import android.widget.TextView;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = "U-REPORT";

    protected Button scanButton;
    protected Button uploadButton;
    protected TextView nbOfContactsFoundLabel;
    protected TableLayout tableLayout;
    protected CheckBox onlyOneNumberPerContactField;

    protected ArrayList<String> phoneNumbers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupUI();
    }

    protected void setupUI() {
        //final String serverUrl = "http://192.168.5.108:8000/";
        final String serverUrl = "https://io.yeleman.com/ucontacts/";

        tableLayout = (TableLayout) findViewById(R.id.tableLayout);
        tableLayout.setVisibility(View.GONE);

        onlyOneNumberPerContactField = (CheckBox) findViewById(R.id.onlyOneNumberPerContactField);

        nbOfContactsFoundLabel = (TextView) findViewById(R.id.nbOfContactsFoundLabel);
        nbOfContactsFoundLabel.setVisibility(View.GONE);

        scanButton = (Button) findViewById(R.id.scanButton);
        uploadButton = (Button) findViewById(R.id.uploadButton);
        uploadButton.setVisibility(View.GONE);

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new UploadTask(serverUrl).execute("");
            }
        });


        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean onlyOnePerContact = onlyOneNumberPerContactField.isChecked();
                new DumpPhoneNumbers(onlyOnePerContact).execute("");
            }
        });
    }

    public void updateUIWithContacts(ArrayList<String> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;

        nbOfContactsFoundLabel.setText(String.valueOf(phoneNumbers.size()));
        nbOfContactsFoundLabel.setVisibility(View.VISIBLE);

        tableLayout.setVisibility(View.VISIBLE);
        uploadButton.setVisibility(View.VISIBLE);
    }

    public void updateUIWithServerReply(JSONObject jsonReply) {
        int nbImported = 0;
        try {
            nbImported = jsonReply.getInt("imported");
            Log.d(TAG, String.valueOf(nbImported));
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        setContentView(R.layout.thank_you);
        TextView thankYouLabel = (TextView) findViewById(R.id.thankYouLabel);
        thankYouLabel.setText(String.format(getString(R.string.thankYouLabel), nbImported));

        TextView nbOfContactsImportedLabel = (TextView) findViewById(R.id.nbOfContactsImportedLabel);
        nbOfContactsImportedLabel.setText(String.format(getString(R.string.nbOfContactsImportedLabel), nbImported));

    }

    public static String getStringFromInputStream(InputStream stream) throws IOException
    {
        int n = 0;
        char[] buffer = new char[1024 * 4];
        InputStreamReader reader = new InputStreamReader(stream, "UTF8");
        StringWriter writer = new StringWriter();
        while (-1 != (n = reader.read(buffer))) writer.write(buffer, 0, n);
        return writer.toString();
    }

    private HttpClient createHttpClient()
    {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
        HttpProtocolParams.setUseExpectContinue(params, true);

        SchemeRegistry schReg = new SchemeRegistry();
        schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schReg.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params, schReg);

        return new DefaultHttpClient(conMgr, params);
    }


    public JSONObject uploadToServerAsJson(String url, JSONArray params) {
        try {
            HttpClient httpClient = createHttpClient();
            HttpPost httpPost = new HttpPost(url);

            StringEntity se = new StringEntity(params.toString(), "UTF-8");
            se.setContentType("application/json;charset=UTF-8");
            httpPost.setEntity(se);

            Log.e(TAG, params.toString());
            HttpResponse httpResponse = httpClient.execute(httpPost);
            HttpEntity httpEntity = httpResponse.getEntity();
            InputStream contentStream = httpEntity.getContent();
            String jsonTxt = getStringFromInputStream(contentStream);
            JSONObject json = new JSONObject(jsonTxt);
            return json;

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
        }
        return null;
    }

    protected ArrayList<String> getAllNumbersFromAddressBook(Boolean onlyOnePerContact) {
        String number = null;
        ArrayList<String> phoneNumbers = new ArrayList<String>();
        ContentResolver cr = getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (cur.getCount() > 0) {
            while (cur.moveToNext()) {
                String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
//                String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                if (Integer.parseInt(cur.getString(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
                    Cursor pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = ?",
                            new String[]{id}, null);

                    int iter = 0;
                    while (pCur.moveToNext()) {
                        number = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        if (phoneNumbers.indexOf(number) == -1) {
                            phoneNumbers.add(number);
                            iter += 1;
                        }
                        if (onlyOnePerContact) {
                            break;
                        }
                    }
                    pCur.close();
                }
            }
        }
        return phoneNumbers;
    }

    public static AlertDialog.Builder getDialogBuilder(Activity activity,
                                                       String title,
                                                       String message,
                                                       boolean cancelable) {
        AlertDialog.Builder smsDialogBuilder = new AlertDialog.Builder(activity);
        smsDialogBuilder.setCancelable(cancelable);
        smsDialogBuilder.setTitle(title);
        smsDialogBuilder.setMessage(message);
        smsDialogBuilder.setIcon(R.drawable.ic_launcher);
        return smsDialogBuilder;
    }

    private class DumpPhoneNumbers extends AsyncTask<String, Integer, String> {

        private Boolean onlyOnePerContact;
        ArrayList<String> phoneNumbers = null;
        private ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);

        public DumpPhoneNumbers(boolean onlyOnePerContact) {
            super();
            this.onlyOnePerContact = onlyOnePerContact;
        }


        @Override
        protected void onPreExecute() {
            // Loading
            progressDialog.setTitle(getString(R.string.scan_in_progress_title));
            progressDialog.setMessage(getString(R.string.scan_in_progress_body));
            progressDialog.setIcon(R.drawable.ic_launcher);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            Log.d(TAG, "onlyOnePerContact: " + String.valueOf(onlyOnePerContact));
            try{
                phoneNumbers = getAllNumbersFromAddressBook(onlyOnePerContact);
            }catch(Exception e){
                Log.d(TAG, e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            updateUIWithContacts(phoneNumbers);
            // after completed finished the progressbar
            progressDialog.dismiss();
        }
    }

    private class UploadTask extends AsyncTask<String, Integer, String> {

        private String serverUrl;
        private JSONObject jsonResult = null;
        private ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);

        public UploadTask(String serverUrl) {
            super();
            this.serverUrl = serverUrl;
        }

        public boolean isOnline() {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            }
            return false;
        }

        @Override
        protected void onPreExecute() {
            // Loading
            if (!isOnline())
                return;
            progressDialog.setTitle(getString(R.string.upload_in_progress_title));
            progressDialog.setMessage(getString(R.string.upload_in_progress_body));
            progressDialog.setIcon(R.drawable.ic_launcher);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            final JSONArray jsArray = new JSONArray(phoneNumbers);

            try{
                jsonResult = uploadToServerAsJson(serverUrl, jsArray);
            }catch(Exception e){
                Log.d(TAG, e.toString());
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            if (isOnline()) {
                // after completed finished the progressbar
                updateUIWithServerReply(jsonResult);
                progressDialog.dismiss();
            }else{
                Activity activity = MainActivity.this;
                AlertDialog.Builder dialogBuilder = getDialogBuilder(
                        activity, activity.getString(R.string.required_connexion_title),
                        activity.getString(R.string.required_connexion_body), true);
                dialogBuilder.setNegativeButton(activity.getString(R.string.required_connexion_cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {}
                });
                dialogBuilder.setPositiveButton(activity.getString(R.string.required_connexion_retry), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        new UploadTask(serverUrl).execute("");
                    }
                });
                AlertDialog dialog = dialogBuilder.create();
                dialog.show();
            }
        }
    }
}
