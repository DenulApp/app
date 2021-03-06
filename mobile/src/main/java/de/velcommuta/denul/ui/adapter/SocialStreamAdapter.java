package de.velcommuta.denul.ui.adapter;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.location.Location;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.ui.IconGenerator;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.List;

import de.velcommuta.denul.R;
import de.velcommuta.denul.data.Friend;
import de.velcommuta.denul.data.GPSTrack;
import de.velcommuta.denul.data.Shareable;
import de.velcommuta.denul.service.DatabaseServiceBinder;

/**
 * RecyclerView adapter for the friendlist
 *
 * The OnClick and OnLongClick implementation is adapted from
 * http://stackoverflow.com/a/27945635/1232833
 * Context menu implementation adapted from
 * http://stackoverflow.com/a/27886458/1232833
 */
public class SocialStreamAdapter extends RecyclerView.Adapter<SocialStreamAdapter.ViewHolder> {
    private List<Shareable> mShares;
    protected DatabaseServiceBinder mBinder;
    protected Context mContext;
    private Fragment mFragment;
    private int mPosition;

    public interface OnItemClickListener {
        /**
         * Called when an item is clicked
         * @param position Position of the item in the list
         */
        void onItemClicked(int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements OnMapReadyCallback{
        protected View mHeldView;
        private TextView mNameView;
        private TextView mNameViewTrailer;
        private ImageView mIconView;
        private FrameLayout mIllustration;
        private TextView mDateView;
        private TextView mFurtherInfoView;
        private TextView mShareCount;
        private ImageView mShareIcon;
        private TextView mDescription;

        // instance variables for track fragments with map
        private GPSTrack mTrack;
        private GoogleMap mMap;
        private Marker mStartMarker;
        private Marker mEndMarker;
        private Polyline mPolyline;

        /**
         * ViewHoldere constructor holding the Reference to a view
         * @param itemView The View to hold
         */
        public ViewHolder(View itemView) {
            super(itemView);
            mHeldView = itemView;
            mNameView = (TextView) itemView.findViewById(R.id.stream_name);
            mNameViewTrailer = (TextView) itemView.findViewById(R.id.stream_name_trailer);
            mIconView = (ImageView) itemView.findViewById(R.id.stream_icon);
            mIllustration = (FrameLayout) itemView.findViewById(R.id.stream_illustration);
            mDateView = (TextView) itemView.findViewById(R.id.stream_date);
            mFurtherInfoView = (TextView) itemView.findViewById(R.id.stream_moreinfo);
            mShareCount = (TextView) itemView.findViewById(R.id.stream_share_count);
            mShareIcon = (ImageView) itemView.findViewById(R.id.stream_share_icon);
            mDescription = (TextView) itemView.findViewById(R.id.stream_description);
        }


        /**
         * Display a shareable in the held view
         * @param share The shareable to display
         */
        public void display(Shareable share) {
            switch (share.getType()) {
                case Shareable.SHAREABLE_TRACK:
                    displayGPSTrack((GPSTrack) share);
                    break;
                // TODO Add further stuff here
            }
        }


        /**
         * Display the data from a GPSTrack object
         * @param track The track to display
         */
        private void displayGPSTrack(GPSTrack track) {
            mTrack = track;
            Friend friend = null;
            if (track.getOwner() != -1) {
                friend = mBinder.getFriendById(track.getOwner());
            }
            if (track.getPosition().size() != 0) {
                if (mMap != null) {
                    drawPath();
                } else {
                    mIllustration.removeAllViews();
                    // Prepare google map options
                    GoogleMapOptions options = new GoogleMapOptions().liteMode(true).mapToolbarEnabled(false);
                    // Initialize new MapView
                    MapView mapView = new MapView(mContext, options);
                    // Add the mapview to the layout
                    mIllustration.addView(mapView);
                    // Call through to the onCreate
                    mapView.onCreate(null);
                    // Initialize
                    mapView.getMapAsync(this);
                    // Set the mapview to not be clickable (to avoid opening google maps on click)
                    mapView.setClickable(false);
                }
            } else {
                // No location data was shared. Add a notice saying as much
                mIllustration.removeAllViews();
                mMap = null;
                TextView textNotice = new TextView(mContext);
                if (friend != null) {
                    textNotice.setText(String.format(mContext.getString(R.string.social_stream_no_location_shared), friend.getName()));
                } else {
                    textNotice.setText(mContext.getString(R.string.social_stream_no_location_available));
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams( FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT );
                lp.gravity = Gravity.CENTER;
                textNotice.setLayoutParams(lp);
                mIllustration.addView(textNotice);
            }
            // Set up the title bar
            if (friend != null) {
                mNameView.setText(friend.getName());
                mNameViewTrailer.setText(String.format(mContext.getString(R.string.social_stream_shared_name), mTrack.getSessionName()));
            } else {
                mNameView.setText(R.string.social_stream_you);
                mNameViewTrailer.setText(String.format(mContext.getString(R.string.social_stream_recorded_name), mTrack.getSessionName()));
            }
            // Set up the mode of transportation icon
            switch (track.getModeOfTransportation()) {
                case GPSTrack.VALUE_RUNNING:
                    mIconView.setImageDrawable(mContext.getResources().getDrawable(R.drawable.ic_running));
                    break;
                case GPSTrack.VALUE_CYCLING:
                    mIconView.setImageDrawable(mContext.getResources().getDrawable(R.drawable.ic_cycling));
                    break;
                default:
                    Log.w("ViewHolder", "display: Unknown Mode of transportation");
                     mIconView.setImageDrawable(mContext.getResources().getDrawable(R.drawable.ic_running));
            }
            // Set up the date
            mDateView.setText(DateTimeFormat.shortDateTime().print(new LocalDateTime(track.getTimestamp(), DateTimeZone.forID(track.getTimezone()))));
            // Set up the distance
            float distance = track.getDistance();
            if (distance < 1000.0f) {
                mFurtherInfoView.setText(String.format(mContext.getString(R.string.distance_m), (int) distance));
            } else {
                mFurtherInfoView.setText(String.format(mContext.getString(R.string.distance_km), (int) distance / 1000.0f));
            }
            // Set up the description
            if (track.getDescription() != null && !track.getDescription().equals("")) {
                mDescription.setText(track.getDescription());
                mDescription.setVisibility(View.VISIBLE);
            } else {
                mDescription.setVisibility(View.GONE);
            }
            // Set up the share count
            int shares = mBinder.getShareRecipientsForShareable(mTrack).size();
            if (shares > 0) {
                mShareIcon.setVisibility(View.VISIBLE);
                mShareCount.setVisibility(View.VISIBLE);
                mShareCount.setText(String.valueOf(shares));
            } else {
                mShareCount.setVisibility(View.GONE);
                mShareIcon.setVisibility(View.GONE);
            }
        }


        @Override
        public void onMapReady(GoogleMap googleMap) {
            mMap = googleMap;
            drawPath();
        }

        /**
         * Draw the path in the {@link GPSTrack} object
         */
        private void drawPath() {
            // if the map has already been drawn to, just return
            if ((mStartMarker != null && mEndMarker != null && mPolyline != null) || mTrack.getPosition().size() == 0) return;
            // Draw start marker
            Location start = mTrack.getPosition().get(0);
            // Set icon for start of route
            IconGenerator ig = new IconGenerator(mContext);
            ig.setStyle(IconGenerator.STYLE_GREEN);
            Bitmap startPoint = ig.makeIcon("Start");
            mStartMarker = mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromBitmap(startPoint))
                    .position(new LatLng(start.getLatitude(), start.getLongitude())));

            // Draw polyline and prepare LatLngBounds object for later camera zoom
            PolylineOptions poptions = new PolylineOptions();
            LatLngBounds.Builder latlngbounds = new LatLngBounds.Builder();
            for (Location l : mTrack.getPosition()) {
                poptions.add(new LatLng(l.getLatitude(), l.getLongitude()));
                latlngbounds.include(new LatLng(l.getLatitude(), l.getLongitude()));
            }
            mPolyline = mMap.addPolyline(poptions);

            // Get final position
            Location finalPos = mTrack.getPosition().get(mTrack.getPosition().size()-1);
            // Set up style
            ig.setStyle(IconGenerator.STYLE_RED);
            Bitmap endPoint = ig.makeIcon("Finish");
            // Create marker
            mEndMarker = mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromBitmap(endPoint))
                    .position(new LatLng(finalPos.getLatitude(), finalPos.getLongitude())));

            // Move the camera to show the whole path
            // Code credit: http://stackoverflow.com/a/14828739/1232833
            int padding = 100;
            final CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(latlngbounds.build(), padding);
            mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    mMap.animateCamera(cu);
                }
            });
        }
    }


    /**
     * Constructor, being passed the dataset to be displayed
     * @param shares A List of {@link Shareable} objects to display
     * @param frag The fragment embedding this List
     * @param ctx A Context
     * @param binder A {@link DatabaseServiceBinder}
     */
    public SocialStreamAdapter(Context ctx, Fragment frag, List<Shareable> shares, DatabaseServiceBinder binder) {
        mShares = shares;
        mFragment = frag;
        mContext = ctx;
        mBinder = binder;
    }


    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.social_stream_item, parent, false);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }


    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.display(mShares.get(position));
        holder.mHeldView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((OnItemClickListener) mFragment).onItemClicked(position);
            }
        });
        holder.mHeldView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                setPosition(holder.getAdapterPosition());
                return false;
            }
        });
    }


    @Override
    public void onViewRecycled(ViewHolder holder) {
        holder.mHeldView.setOnLongClickListener(null);
        super.onViewRecycled(holder);
    }


    @Override
    public int getItemCount() {
        return mShares.size();
    }


    /**
     * Get the Friend at the specified position in the List
     * @param position The position
     * @return The friend at that position
     */
    public Shareable getShareableAt(int position) {
        return mShares.get(position);
    }


    /**
     * Get the current position (helper for the Context menu implementation)
     * @return Current position
     */
    public int getPosition() {
        return mPosition;
    }


    /**
     * Set the current position (helper for the Context menu implementation)
     * @param pos Position
     */
    private void setPosition(int pos) {
        mPosition = pos;
    }
}
