package com.example.jodrew.heartratebandapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Button;
import android.view.View.OnClickListener;
import android.app.Activity;
import android.os.AsyncTask;
import android.widget.Toast;
import java.util.Timer;
import java.util.TimerTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import android.os.Handler;

//Band References
import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandIOException;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandRRIntervalEvent;
import com.microsoft.band.sensors.BandRRIntervalEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;

public class MainActivity extends AppCompatActivity {

    public long msElapsed;
    public boolean isRunning = false;

    private BandClient client = null;
    private Button btnStart, btnStop;
    private TextView txtStatusHeart, txtStatusGsr, txtStatusRRI;
    private ArrayList HR = new ArrayList();
    private ArrayList GSR = new ArrayList();
    private ArrayList RRI = new ArrayList();
    private ArrayList time = new ArrayList();
    private ArrayList times = new ArrayList();
    private int secs;
    private int mins;

    private long startTime = 0;
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;

            secs = seconds;
            mins = minutes;


            timerHandler.postDelayed(this, 500);
        }
    };


    public String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/a";

    private BandRRIntervalEventListener mRRIntervalEventListener = new BandRRIntervalEventListener() {
        @Override
        public void onBandRRIntervalChanged(final BandRRIntervalEvent event) {
            if (event != null) {
                RRIappendToUI(String.format("RR Interval = %.3f s\n", event.getInterval()));
                RRI.add(event.getInterval());
            }
        }
    };

    private BandHeartRateEventListener mHeartRateEventListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if (event != null) {
                HeartappendToUI(String.format("Heart Rate = %d beats per minute\n", event.getHeartRate()));
                HR.add(event.getHeartRate());

            }
        }
    };

    private BandGsrEventListener mGsrEventListener = new BandGsrEventListener() {
        @Override
        public void onBandGsrChanged(final BandGsrEvent event) {
            if (event != null) {
                GsrappendToUI(String.format("Resistance = %d kOhms\n", event.getResistance()));
                GSR.add(event.getResistance());
                time.add(mins);
                times.add(secs);

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //set heart rate
        txtStatusHeart = (TextView) findViewById(R.id.txtStatusHeart);
        txtStatusGsr = (TextView) findViewById(R.id.txtStatusGsr);
        txtStatusRRI = (TextView) findViewById(R.id.txtStatusRRI);

        //set start heart rate
        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);


        btnStart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                txtStatusHeart.setText("");
                txtStatusGsr.setText("");
                txtStatusRRI.setText("");
                Chronometer chronometer = (Chronometer) findViewById(R.id.chronometer);
                startTimer(chronometer);
                new SubscriptionTask().execute();

                timerHandler.removeCallbacks(timerRunnable);
                startTime = System.currentTimeMillis();
                timerHandler.postDelayed(timerRunnable, 0);
            }
        });

        btnStop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Chronometer chronometer = (Chronometer) findViewById(R.id.chronometer);
                onPause(chronometer);
            }
        });

        File dir = new File(path);
        dir.mkdirs();
    }

    //Kick off the reading
    private class SubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    if (client.getSensorManager().getCurrentHeartRateConsent() == UserConsent.GRANTED) {
                        client.getSensorManager().registerHeartRateEventListener(mHeartRateEventListener);
                        client.getSensorManager().registerRRIntervalEventListener(mRRIntervalEventListener);
                    } else {
                        HeartappendToUI("You have not given this application consent to access heart rate or RRI data yet."
                                + " Please press the Heart Rate Consent button.\n");
                    }
                    client.getSensorManager().registerGsrEventListener(mGsrEventListener);
                } else {
                    HeartappendToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage = "";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                HeartappendToUI(exceptionMessage);

            } catch (Exception e) {
                HeartappendToUI(e.getMessage());
            }
            return null;
        }


    }

    protected Void startTimer(Chronometer chronometer) {
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
        return null;
    }

    //Get connection to band
    private boolean getConnectedBandClient() throws InterruptedException, BandException {

        if (client == null) {
            //Find paired bands
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                //No bands found...message to user
                return false;
            }
            //need to set client if there are devices
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }

        //need to return connected status
        return ConnectionState.CONNECTED == client.connect().await();
    }


    protected void onResume(Chronometer chronometer) {
        super.onResume();
        txtStatusHeart.setText("");
        txtStatusRRI.setText("");
        txtStatusGsr.setText("");
        chronometer.start();
    }

    protected void onPause(Chronometer chronometer) {
        super.onPause();
        if (client != null) {
            try {
                client.getSensorManager().unregisterHeartRateEventListener(mHeartRateEventListener);
                client.getSensorManager().unregisterGsrEventListener(mGsrEventListener);
                client.getSensorManager().unregisterRRIntervalEventListener(mRRIntervalEventListener);
            } catch (BandIOException e) {
                HeartappendToUI(e.getMessage());
            }
        }
        chronometer.stop();

        File file = new File(path + "/save.txt");

        int x = GSR.size();
        String[] saveText = new String[x+2];
        //long timeElapsed = msElapsed;
        saveText[0] = "Time:    HR:    GSR:   RRI:\nm s       bpm   kOhms    --";
        saveText[1] = "";

        for (int i = 2; i < x; i++) {
            saveText[1] += ("\n" + time.get(i) + " " + times.get(i) + "      " + HR.get(i) + "      " + GSR.get(i) + "  " + String.format("%.4f", RRI.get(i)) + " ");
        }
        saveText[1] += "\nAverages:";
        int j = 0;
        int i = 0, ave = 0;
        float HHRdata = 0, GSRdata = 0, RRIdata = 0;
        //newest one!
        while(Integer.valueOf(time.get(i).toString()) == j)
        {
            while(time.get(i) == j)
            {
                HHRdata += Float.valueOf(HR.get(i).toString());
                GSRdata += Float.valueOf(GSR.get(i).toString());
                RRIdata += Float.valueOf(RRI.get(i).toString());
                i++;
                ave++;
            }
            saveText[1] += saveText[1] += ("\n" + time.get(i) + HHRdata/ave + "      " + GSRdata/ave + "  " + RRIdata/ave + " ");
            ave = 0;
            j++;
        }

        //Toast.makeText(getApplicationContext(), "Sazed", Toast.LENGTH_LONG).show();
        //Save(file, saveText);
        timerHandler.removeCallbacks(timerRunnable);
        EndExperiment(saveText);

    }


    public static void Save(File file, String[] data)
    {
        FileOutputStream fos = null;
        try
        {
            fos = new FileOutputStream(file);
        }
        catch (FileNotFoundException e) {e.printStackTrace();}
        try
        {
            try
            {
                for (int i = 0; i<data.length; i++)
                {
                    fos.write(data[i].getBytes());
                    if (i < data.length-1)
                    {
                        fos.write("\n".getBytes());
                    }
                }
            }
            catch (IOException e) {e.printStackTrace();}
        }
        finally
        {
            try
            {
                fos.close();
            }
            catch (IOException e) {e.printStackTrace();}
        }
    }
    
    @Override
    protected void onDestroy() {
        if (client != null) {
            try {
                client.disconnect().await();
            } catch (InterruptedException e) {
                // Do nothing as this is happening during destroy
            } catch (BandException e) {
                // Do nothing as this is happening during destroy
            }
        }
        super.onDestroy();
    }

    private void HeartappendToUI(final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusHeart.setText(string);
            }
        });
    }

    private void RRIappendToUI(final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusRRI.setText(string);
            }
        });
    }

    private void GsrappendToUI(final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatusGsr.setText(string);
            }
        });
    }


    public void EndExperiment(String[] saveText){
        //we gotta find the path, but it's in /savedFile.txt



        String[] email = {"talresearchlab@gmail.com"};
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, email);
        //here we need to say "participant #1: names
        //need to input string name
        intent.putExtra(Intent.EXTRA_SUBJECT, OverlayScreen.Name + " " + OverlayScreen.Date + " " + OverlayScreen.Time);

        intent.putExtra(Intent.EXTRA_TEXT, saveText[0] + saveText[1]);

        //here you pass in the entire file
        //start it
        intent.setType("message/rfc822");
        startActivity(Intent.createChooser(intent, "Send email..."));
    }



}
