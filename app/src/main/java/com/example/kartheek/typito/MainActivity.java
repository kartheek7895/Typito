package com.example.kartheek.typito;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_OPENER = 1;
    ProgressDialog progress;
    GoogleApiClient mGoogleApiClient;
    Button uv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progress=new ProgressDialog(this);
        progress.setMessage("Uploading");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setProgress(0);
        uv=(Button)findViewById(R.id.button);
        uv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                startActivityForResult(Intent.createChooser(intent,"Select Video"),2);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if(resultCode==RESULT_OK){
            if(requestCode==2){
                Uri videoUri=data.getData();
                //String filemgr=videoUri.getPath();
                String videoPath=getPath(videoUri);

                if(videoPath!=null){
                    saveFiletoDrive(videoPath);
                    getProgressDialog().show();
                    //Toast.makeText(MainActivity.this,videoPath,Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    public String getPath(Uri uri) {
        String[] projection = {MediaStore.Video.Media.DATA};
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } else
            return null;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());

        if (!result.hasResolution()) {

            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }

        try {

            result.startResolutionForResult(this, 1);

        } catch (IntentSender.SendIntentException e) {

            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }
    final public ResultCallback < DriveFolder.DriveFileResult > fileCallback = new
            ResultCallback < DriveFolder.DriveFileResult > () {
                @Override
                public void onResult(DriveFolder.DriveFileResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.i(TAG, "Error while trying to create the file");
                        return;
                    }
                    Log.i(TAG, "Successfull !");

                }
            };

    private void saveFiletoDrive(final String file){
        Log.i(TAG, "Saving....");
        Drive.DriveApi.newDriveContents(mGoogleApiClient).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
            @Override
            public void onResult(@NonNull DriveApi.DriveContentsResult result) {
                String mime = "video/mp4";
                if (!result.getStatus().isSuccess()) {
                    Log.i(TAG, "Failed to create new contents.");
                    return;
                }
                OutputStream ostream=result.getDriveContents().getOutputStream();
                FileInputStream fistream;
                try{
                    File file2=new File(file);
                    fistream=new FileInputStream(file2);
                    ByteArrayOutputStream baostream=new ByteArrayOutputStream();
                    byte[] buf=new byte[1024];int n;
                    while ((n = fistream.read(buf)) != -1) {
                        baostream.write(buf, 0, n);
                        progress.incrementProgressBy(n/100);

                    }
                    getProgressDialog().setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            Toast.makeText(MainActivity.this,"Cancelled",Toast.LENGTH_SHORT).show();
                        }
                    });
                    ostream.write(baostream.toByteArray());
                    baostream.flush();

                    ostream.close();
                    ostream = null;
                    fistream.close();
                    fistream = null;
                }catch (FileNotFoundException e){
                    Log.w(TAG, "FileNotFoundException: " + e.getMessage());
                }catch (IOException e1){
                    Log.w(TAG, "FileNotFoundException: " + e1.getMessage());
                }
                MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                        .setMimeType(mime).setTitle(file).build();

                Drive.DriveApi.getRootFolder(getGoogleApiClient())
                        .createFile(getGoogleApiClient(), metadataChangeSet, result.getDriveContents())
                        .setResultCallback(fileCallback);

                Log.i(TAG, "Creating new video on Drive (" + file + ")");
            }
        });
    }
    public GoogleApiClient getGoogleApiClient(){
        return this.mGoogleApiClient;
    }
    public ProgressDialog getProgressDialog(){
        return this.progress;
    }
}
