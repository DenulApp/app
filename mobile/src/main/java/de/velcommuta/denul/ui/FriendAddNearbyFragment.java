package de.velcommuta.denul.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AppIdentifier;
import com.google.android.gms.nearby.connection.AppMetadata;
import com.google.android.gms.nearby.connection.Connections;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import de.velcommuta.denul.R;
import de.velcommuta.denul.ui.adapter.NearbyConnection;
import de.velcommuta.denul.ui.adapter.NearbyConnectionListAdapter;

/**
 * Fragment to show the Google Nearby workflow.
 * TODO At the moment, this is a pretty basic protocol. I will have to go back and update it once
 * TODO User profiles, avatars etc are a thing. It also needs better error handling
 */
public class FriendAddNearbyFragment extends Fragment implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        Connections.ConnectionRequestListener,
        Connections.MessageListener,
        Connections.EndpointDiscoveryListener,
        AdapterView.OnItemClickListener {

    private static final String TAG = "FriendAddNearby";
    private GoogleApiClient mGoogleApiClient;

    private KexProvider mListener;
    private ListView mDeviceList;
    private TextView mEmptyView;
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private NearbyConnectionListAdapter mListAdapter;
    private Hashtable<String, NearbyConnection> mIdMap;

    private boolean isInitiator;

    // Timeouts for google nearby. 0L => Discover / advertise until explicitly stopped
    private static final long TIMEOUT_ADVERTISE = 0L;
    private static final long TIMEOUT_DISCOVER = 0L;

    // The ID of the other endpoint for Google Nearby
    private String mOtherEndpointId;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment StepCountFragment.
     */
    public static FriendAddNearbyFragment newInstance() {
        return new FriendAddNearbyFragment();
    }


    /**
     * Required empty constructor
     */
    public FriendAddNearbyFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_add_friend_nearby, container, false);
        // Initialize the device list data structure
        ArrayList<NearbyConnection> adapterList = new ArrayList<>();
        mIdMap = new Hashtable<>();
        // Grab reference to Device List
        mDeviceList = (ListView) v.findViewById(R.id.addfriend_step2_nearby_devicelist);
        // Set up message to display while no entries are in the list
        mEmptyView = (TextView) v.findViewById(R.id.addfriend_step2_nearby_emptyview);
        mDeviceList.setEmptyView(mEmptyView);
        // Get reference to Progress Bar
        mProgressBar = (ProgressBar) v.findViewById(R.id.addfriend_step2_nearby_progressbar);
        mProgressText = (TextView) v.findViewById(R.id.addfriend_step2_nearby_progressnote);
        // Set up ArrayAdapter
        mListAdapter = new NearbyConnectionListAdapter(getActivity(), adapterList);
        mDeviceList.setAdapter(mListAdapter);
        mDeviceList.setOnItemClickListener(this);
        // Initialize Google API client
        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Nearby.CONNECTIONS_API)
                .build();
        return v;
    }


    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
        if (!isConnectedToNetwork()) {
            mEmptyView.setText(R.string.friend_add_step2_nearby_emptyview_nowifi);
        } else {
            mEmptyView.setText(R.string.friend_add_step2_nearby_emptyview);
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mGoogleApiClient != null) {
            mListAdapter.clear();
            stopAdvertising();
            stopDiscovery();
            Nearby.Connections.stopAllEndpoints(mGoogleApiClient);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onAttach(Activity act) {
        super.onAttach(act);
        try {
            mListener = (KexProvider) act;
        } catch (ClassCastException e) {
            throw new ClassCastException(act.toString()
                    + " must implement KexProvider");
        }
    }

    /**
     * Check if the device is connected (or connecting) to a WiFi network.
     * @return true if connected or connecting, false otherwise.
     */
    private boolean isConnectedToNetwork() {
        ConnectivityManager connManager = (ConnectivityManager)
                getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        return (info != null && info.isConnectedOrConnecting());
    }


    /**
     * Stop advertising for Nearby Connections
     */
    private void stopAdvertising() {
        Nearby.Connections.stopAdvertising(mGoogleApiClient);
    }


    /**
     * Begin advertising for Nearby Connections, if possible.
     */
    private void startAdvertising() {
        Log.d(TAG, "startAdvertising");
        if (!isConnectedToNetwork()) {
            Log.d(TAG, "startAdvertising: not connected to WiFi network.");
            return;
        }

        // Advertising with an AppIdentifer lets other devices on the network discover
        // this application and prompt the user to install the application.
        List<AppIdentifier> appIdentifierList = new ArrayList<>();
        appIdentifierList.add(new AppIdentifier(getActivity().getPackageName()));
        AppMetadata appMetadata = new AppMetadata(appIdentifierList);

        // Advertise for Nearby Connections. This will broadcast the service id defined in
        // AndroidManifest.xml. By passing 'null' for the name, the Nearby Connections API
        // will construct a default name based on device model such as 'LGE Nexus 5'.
        String name = null;
        Nearby.Connections.startAdvertising(mGoogleApiClient, name, appMetadata, TIMEOUT_ADVERTISE,
                this).setResultCallback(new ResultCallback<Connections.StartAdvertisingResult>() {
            @Override
            public void onResult(Connections.StartAdvertisingResult result) {
                Log.d(TAG, "startAdvertising:onResult:" + result);
                if (result.getStatus().isSuccess()) {
                    Log.d(TAG, "startAdvertising:onResult: SUCCESS");
                } else {
                    Log.d(TAG, "startAdvertising:onResult: FAILURE ");

                    // If the user hits 'Advertise' multiple times in the timeout window,
                    // the error will be STATUS_ALREADY_ADVERTISING
                    int statusCode = result.getStatus().getStatusCode();
                    if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                        Log.d(TAG, "STATUS_ALREADY_ADVERTISING");
                    } else {
                        Log.d(TAG, "startAdvertising: Advertising");
                    }
                }
            }
        });
    }


    /**
     * Stop discovering devices advertising Nearby Connections, if possible
     */
    private void stopDiscovery() {
        String serviceID = getString(R.string.nearby_service_identifier);
        Nearby.Connections.stopDiscovery(mGoogleApiClient, serviceID);
    }


    /**
     * Begin discovering devices advertising Nearby Connections, if possible.
     */
    private void startDiscovery() {
        Log.d(TAG, "startDiscovery");
        if (!isConnectedToNetwork()) {
            Log.d(TAG, "startDiscovery: not connected to WiFi network.");
            return;
        }

        // Discover nearby apps that are advertising with the required service ID.
        String serviceId = getString(R.string.nearby_service_identifier);
        Nearby.Connections.startDiscovery(mGoogleApiClient, serviceId, TIMEOUT_DISCOVER, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.d(TAG, "startDiscovery:onResult: SUCCESS");
                        } else {
                            Log.d(TAG, "startDiscovery:onResult: FAILURE");

                            // If the user hits 'Discover' multiple times in the timeout window,
                            // the error will be STATUS_ALREADY_DISCOVERING
                            int statusCode = status.getStatusCode();
                            if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                                Log.d(TAG, "STATUS_ALREADY_DISCOVERING");
                            } else {
                                Log.d(TAG, "startDiscovery: Discovering");
                            }
                        }
                    }
                });
    }

    /**
     * Send a reliable message to the connected peer. Takes the contents of the EditText and
     * sends the message as a byte[].
     * @param msg The message to send, as byte[]
     */
    private void sendMessage(byte[] msg) {
        // Sends a reliable message, which is guaranteed to be delivered eventually and to respect
        // message ordering from sender to receiver. Nearby.Connections.sendUnreliableMessage
        // should be used for high-frequency messages where guaranteed delivery is not required, such
        // as showing one player's cursor location to another. Unreliable messages are often
        // delivered faster than reliable messages.
        Nearby.Connections.sendReliableMessage(mGoogleApiClient, mOtherEndpointId, msg);
    }

    
    /**
     * Send a connection request to a given endpoint.
     * @param endpointId the endpointId to which you want to connect.
     * @param endpointName the name of the endpoint to which you want to connect. Not required to
     *                     make the connection, but used to display after success or failure.
     */
    private void connectTo(String endpointId, final String endpointName) {
        Log.d(TAG, "connectTo:" + endpointId + ":" + endpointName);

        // Send a connection request to a remote endpoint. By passing 'null' for the name,
        // the Nearby Connections API will construct a default name based on device model
        // such as 'LGE Nexus 5'.
        setModeSettingUp();
        String myName = null;
        byte[] myPayload = null;  // TODO Maybe use this payload to transmit user information? Nick, avatar, used server, server cert FP, ...
        Nearby.Connections.sendConnectionRequest(mGoogleApiClient, myName, endpointId, myPayload,
                new Connections.ConnectionResponseCallback() {
                    @Override
                    public void onConnectionResponse(String endpointId, Status status,
                                                     byte[] bytes) {
                        Log.d(TAG, "onConnectionResponse:" + endpointId + ":" + status);
                        if (status.isSuccess()) {
                            Log.d(TAG, "onConnectionResponse: " + endpointName + " SUCCESS");
                            mOtherEndpointId = endpointId;
                            isInitiator = true;
                            setModeConnected();
                            sendKex();
                        } else {
                            Log.d(TAG, "onConnectionResponse: " + endpointName + " FAILURE");
                            setModeScanning();
                        }
                    }
                }, this);
    }
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected: Connected, starting to advertise and discover");
        startAdvertising();
        startDiscovery();
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: " + i);

        // Try to re-connect
        mGoogleApiClient.reconnect();
    }


    @Override
    public void onConnectionRequest(final String endpointId, String deviceId, String endpointName,
                                    byte[] payload) {
        Log.d(TAG, "onConnectionRequest:" + endpointId + ":" + endpointName);

        // This device is advertising and has received a connection request. Show a dialog asking
        // the user if they would like to connect and accept or reject the request accordingly.
        AlertDialog ConnectionRequestDialog = new AlertDialog.Builder(getActivity())
                .setTitle("Connection Request")
                .setMessage("Do you want to connect to " + endpointName + "?")
                .setCancelable(false)
                .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        byte[] payload = new byte[]{0x00};  // TODO Maybe use this payload to transmit user information? Nick, avatar, used server, server cert FP, ...
                        Nearby.Connections.acceptConnectionRequest(mGoogleApiClient, endpointId,
                                payload, FriendAddNearbyFragment.this)
                                .setResultCallback(new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(Status status) {
                                        if (status.isSuccess()) {
                                            Log.d(TAG, "acceptConnectionRequest: SUCCESS");
                                            mOtherEndpointId = endpointId;
                                            isInitiator = false;
                                            setModeSettingUp();
                                            setModeConnected();
                                        } else {
                                            Log.d(TAG, "acceptConnectionRequest: FAILURE");
                                            setModeScanning();
                                        }
                                    }
                                });
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Nearby.Connections.rejectConnectionRequest(mGoogleApiClient, endpointId);
                    }
                }).create();

        ConnectionRequestDialog.show();
    }


    @Override
    public void onEndpointFound(final String endpointId, String deviceId, String serviceId,
                                final String endpointName) {
        Log.d(TAG, "onEndpointFound:" + endpointId + ":" + endpointName);

        // This device is discovering endpoints and has located an advertiser. Add it to the list
        // of found devices
        NearbyConnection conn = new NearbyConnection(endpointName, endpointId);
        mListAdapter.add(conn);
        mIdMap.put(endpointId, conn);
    }


    @Override
    public void onEndpointLost(String endpointId) {
        Log.d(TAG, "onEndpointLost:" + endpointId);
        mListAdapter.remove(mIdMap.get(endpointId));
        mIdMap.remove(endpointId);
    }


    @Override
    public void onDisconnected(String endpointID) {
        Log.d(TAG, "onDisconnected: " + endpointID + " disconnected");
    }


    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: Connection failed");
    }


    @Override
    public void onMessageReceived(String endpointId, byte[] payload, boolean isReliable) {
        // A message has been received from a remote endpoint.
        Log.d(TAG, "onMessageReceived: Received message");
        if (!isInitiator) {
            sendKex();
        }
        setModeCalculatingKeys();
        mListener.putKexData(payload);
        mListener.kexDone(isInitiator);
        setModeDone();
    }


    /**
     * Set the UI into the mode used during scanning
     */
    private void setModeScanning() {
        mDeviceList.setVisibility(View.VISIBLE);
        mProgressText.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
    }


    /**
     * Set up the UI into the mode used during the key exchange
     */
    private void setModeSettingUp() {
        // Hide client list
        mDeviceList.setVisibility(View.GONE);
        // Make ProgressBar visible
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBar.setMax(3);
        mProgressBar.setProgress(0);
        mProgressText.setVisibility(View.VISIBLE);
        mProgressText.setText(R.string.friend_add_step2_nearby_state_connecting);
    }


    /**
     * Set up the UI into the "connected" state
     */
    private void setModeConnected() {
        mProgressBar.setProgress(1);
        mProgressText.setText(R.string.friend_add_step2_nearby_state_exchanging);
    }


    /**
     * Set up the UI into the "Key exchange done, calculating keys" mode
     */
    private void setModeCalculatingKeys() {
        mProgressBar.setProgress(2);
        mProgressText.setText(R.string.friend_add_step2_nearby_state_calculating);
    }


    /**
     * Set up the UI into the "everything" mode
     */
    private void setModeDone() {
        mProgressBar.setProgress(3);
        mProgressText.setText(R.string.friend_add_step2_nearby_state_done);
    }


    /**
     * Send key exchange data to the partner
     */
    private void sendKex() {
        sendMessage(mListener.getPublicKexData());
    }


    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        NearbyConnection conn = (NearbyConnection) adapterView.getItemAtPosition(position);
        connectTo(conn.getID(), conn.getName());
    }


    /**
     * Interface for communication with the hosting activity
     */
    public interface KexProvider {
        /**
         * Receive key exchange data
         * @param kex Key exchange data as a byte[]
         */
        void putKexData(byte[] kex);

        /**
         * Get the public Kex data
         * @return public key exchange data, as byte[]
         */
        byte[] getPublicKexData();

        /**
         * Called to notify the hosting activity that the key exchange is done
         * @param isInitiating Has this device initiated the connection?
         */
        void kexDone(boolean isInitiating);
    }
}
