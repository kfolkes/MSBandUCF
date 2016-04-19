package com.example.jodrew.heartratebandapp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.view.View.OnClickListener;
import android.app.Activity;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;

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

    private BandClient client = null;
    private Button btnStart, btnConsent;
    private TextView txtStatusHeart, txtStatusGsr, txtStatusRRI;

    private BandRRIntervalEventListener mRRIntervalEventListener = new BandRRIntervalEventListener() {
        @Override
        public void onBandRRIntervalChanged(final BandRRIntervalEvent event) {
            if (event != null) {
                RRIappendToUI(String.format("RR Interval = %.3f s\n", event.getInterval()));
            }
        }
    };

    private BandHeartRateEventListener mHeartRateEventListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if (event != null) {
                HeartappendToUI(String.format("Heart Rate = %d beats per minute\n"
                        + "Quality = %s\n", event.getHeartRate(), event.getQuality()));
            }
        }
    };

    private BandGsrEventListener mGsrEventListener = new BandGsrEventListener() {
        @Override
        public void onBandGsrChanged(final BandGsrEvent event) {
            if (event != null) {
                GsrappendToUI(String.format("Resistance = %d kOhms\n", event.getResistance()));
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

        //set consent
        btnConsent = (Button) findViewById(R.id.btnConsent);

        //set start heart rate
        btnStart = (Button) findViewById(R.id.btnStart);

        final WeakReference<Activity> reference = new WeakReference<Activity>(this);

        btnConsent.setOnClickListener(new OnClickListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onClick(View v) {
                new ConsentTask().execute(reference);
            }
        });

        btnStart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                txtStatusHeart.setText("");
                txtStatusGsr.setText("");
                txtStatusRRI.setText("");
                new SubscriptionTask().execute();
            }
        });



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
                String exceptionMessage="";
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



    //Need to get user consent
    private class ConsentTask extends AsyncTask<WeakReference<Activity>, Void, Void> {
        @Override
        protected Void doInBackground(WeakReference<Activity>... params) {
            try {
                if (getConnectedBandClient()) {

                    if (params[0].get() != null) {
                        client.getSensorManager().requestHeartRateConsent(params[0].get(), new HeartRateConsentListener() {
                            @Override
                            public void userAccepted(boolean consentGiven) {
                            }
                        });
                    }
                } else {
                    HeartappendToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
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
        } else if(ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }

        //need to return connected status
        return ConnectionState.CONNECTED == client.connect().await();
    }

    @Override
    protected void onResume() {
        super.onResume();
        txtStatusHeart.setText("");
        txtStatusRRI.setText("");
        txtStatusGsr.setText("");
    }
    
    @Override
    protected void onPause() {
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


}
