package com.Schedular.Schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.Schedular.Database.DatabaseHelper;
import com.Schedular.R;
import com.Schedular.Vuforia.Application.ApplicationControl;
import com.Schedular.Vuforia.Application.ApplicationException;
import com.Schedular.Vuforia.Application.ApplicationSession;
import com.Schedular.Vuforia.Utilities.LoadingDialogHandler;
import com.Schedular.Vuforia.Utilities.ApplicationGLView;
import com.Schedular.Vuforia.Utilities.Texture;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.ObjectTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

public class Schedules extends Activity implements ApplicationControl
{
    // Activity Keys
    public static final String SectionNumberKey = "com.Schedular.Scheule.SectionNumberKey";
    public static final String InstructorKeys = "com.Schedular.Scheule.InstructorKeys";
    public static final String InstructorValues = "com.Schedular.Scheule.InstructorValues";
    public static final String CourseKeys = "com.Schedular.Scheule.CourseKeys";
    public static final String CourseValues = "com.Schedular.Scheule.CourseValues";

    // Static Final Variables
    static final int HIDE_STATUS_BAR = 0;
    static final int SHOW_STATUS_BAR = 1;
    static final int HIDE_2D_OVERLAY = 0;
    static final int SHOW_2D_OVERLAY = 1;
    static final int HIDE_LOADING_DIALOG = 0;
    static final int SHOW_LOADING_DIALOG = 1;
    static final String LOGTAG = "Schedules";
    private static final int SCHEDULEINFO_NOT_DISPLAYED = 0;
    private static final int SCHEDULEINFO_IS_DISPLAYED = 1;

    private static int mTextureSize = 768;
    ApplicationSession vuforiaAppSession;
    String currentTrackableId;
    Trackable currentTrackable;
    private int mScheduleInfoStatus = SCHEDULEINFO_NOT_DISPLAYED;
    private String mStatusBarText;
    private Schedule mScheduleData;
    private ApplicationGLView mGlView;
    private SchedulesRenderer mRenderer;
    private Texture mTexture;
    private ConstraintLayout mUILayout;
    private TextView mStatusBar;
    private ImageButton mCloseButton;
    private ImageButton mNextButton;
    private ImageButton mPreviousButton;
    private ImageButton mInformationButton;
    private AlertDialog mErrorDialog;
    private GestureDetector mGestureDetector;
    private Handler statusBarHandler = new StatusBarHandler ( this );
    private Handler overlay2DHandler = new Overlay2dHandler ( this );
    private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler ( this );
    private float mdpiScaleIndicator;
    private Activity mActivity = null;
    private DatabaseHelper mDBHelper;
    private SQLiteDatabase mDb;
    private ArrayList<Row> activeRows;
    private DataSet mCurrentDataset;
    private ArrayList<String> mDatasetStrings = new ArrayList<> ( );
    private int activeRowsIndex = 0;
    private boolean shouldUpdateTexture = false;

    private void initStateVariables ( )
    {
        mRenderer.setRenderState ( SchedulesRenderer.RS_SCANNING );
        mRenderer.setProductTexture ( null );
        mRenderer.setScanningMode ( true );
        mRenderer.isShowing2DOverlay ( false );
        mRenderer.showAnimation3Dto2D ( false );
        mRenderer.stopTransition3Dto2D ( );
        mRenderer.stopTransition2Dto3D ( );

        currentTrackableId = "";
        currentTrackable = null;
    }

    // Sets current device Scale factor based on screen dpi
    public void setDeviceDPIScaleFactor ( float dpiSIndicator )
    {
        mRenderer.setDPIScaleIndicator ( dpiSIndicator );

        // MDPI devices
        if ( dpiSIndicator <= 1.0f )
        {
            mRenderer.setScaleFactor ( 1.6f );
        }
        // HDPI devices
        else if ( dpiSIndicator <= 1.5f )
        {
            mRenderer.setScaleFactor ( 1.3f );
        }
        // XHDPI devices
        else if ( dpiSIndicator <= 2.0f )
        {
            mRenderer.setScaleFactor ( 1.0f );
        }
        // XXHDPI devices
        else
        {
            mRenderer.setScaleFactor ( 0.6f );
        }
    }

    // Called when the activity first starts or needs to be recreated after resuming the application
    // or a configuration change.
    @Override
    protected void onCreate ( Bundle savedInstanceState )
    {
        Log.d ( LOGTAG, "onCreate" );
        super.onCreate ( savedInstanceState );

        mDBHelper = new DatabaseHelper ( this );
        try
        {
            mDBHelper.updateDataBase ( );
        }
        catch ( IOException mIOException )
        {
            throw new Error ( "Unable To Update Database" );
        }
        mDb = mDBHelper.getWritableDatabase ( );
        Log.d ( LOGTAG, "Database Name : " + mDBHelper.getDatabaseName ( ) );

        mDatasetStrings.add ( "Schedule.xml" );

        mActivity = this;

        vuforiaAppSession = new ApplicationSession ( this );

        startLoadingAnimation ( );

        vuforiaAppSession.initAR ( this, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR );

        // Creates the GestureDetector listener for processing double tap
        mGestureDetector = new GestureDetector ( this, new GestureListener ( ) );

        mdpiScaleIndicator = getApplicationContext ( ).getResources ( ).getDisplayMetrics ( ).density;

        // Use an OrientationChangeListener here to capture all orientation changes.  Android
        // will not send an Activity.onConfigurationChanged() callback on a 180 degree rotation,
        // ie: Left Landscape to Right Landscape.  Vuforia needs to react to this change and the
        // ApplicationSession needs to update the Projection Matrix.
        OrientationEventListener orientationEventListener = new OrientationEventListener ( mActivity )
        {
            int mLastRotation = -1;

            @Override
            public void onOrientationChanged ( int i )
            {
                int activityRotation = mActivity.getWindowManager ( ).getDefaultDisplay ( ).getRotation ( );
                if ( mLastRotation != activityRotation )
                {
                    // Update video background for 180 degree rotation
                    if ( Math.abs ( mLastRotation - activityRotation ) == 2 && mRenderer != null )
                    {
                        mRenderer.updateVideoBackground ( );
                    }
                    mLastRotation = activityRotation;
                }
            }
        };

        if ( orientationEventListener.canDetectOrientation ( ) )
        {
            orientationEventListener.enable ( );
        }
    }

    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume ( )
    {
        Log.d ( LOGTAG, "onResume" );
        super.onResume ( );

        showProgressIndicator ( true );
        vuforiaAppSession.onResume ( );

        mScheduleInfoStatus = SCHEDULEINFO_NOT_DISPLAYED;

        // By default the 2D Overlay is hidden
        hide2DOverlay ( );
    }

    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged ( Configuration config )
    {
        Log.d ( LOGTAG, "onConfigurationChanged" );
        super.onConfigurationChanged ( config );

        vuforiaAppSession.onConfigurationChanged ( );
    }

    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause ( )
    {
        Log.d ( LOGTAG, "onPause" );
        super.onPause ( );

        try
        {
            vuforiaAppSession.pauseAR ( );
        }
        catch ( ApplicationException exception )
        {
            Log.e ( LOGTAG, exception.getString ( ) );
        }

        if ( mRenderer != null )
        {
            initStateVariables ( );
        }

        if ( mGlView != null )
        {
            mGlView.setVisibility ( View.INVISIBLE );
            mGlView.onPause ( );
        }
    }

    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy ( )
    {
        Log.d ( LOGTAG, "onDestroy" );
        super.onDestroy ( );

        try
        {
            vuforiaAppSession.stopAR ( );
        }
        catch ( ApplicationException exception )
        {
            Log.e ( LOGTAG, exception.getString ( ) );
        }

        System.gc ( );
    }

    private void startLoadingAnimation ( )
    {
        // Inflates the Overlay Layout to be displayed above the Camera View
        LayoutInflater inflater = LayoutInflater.from ( this );
        mUILayout = ( ConstraintLayout ) inflater.inflate ( R.layout.schedule_camera_overlay, null, false );

        mUILayout.setVisibility ( View.VISIBLE );
        mUILayout.setBackgroundColor ( Color.TRANSPARENT );

        // By default
        loadingDialogHandler.mLoadingDialogContainer = mUILayout.findViewById ( R.id.cameraLoadingBar );
        loadingDialogHandler.mLoadingDialogContainer.setVisibility ( View.VISIBLE );

        addContentView ( mUILayout, new ViewGroup.LayoutParams ( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) );

        // Gets a Reference to the Bottom Status Bar
        mStatusBar = ( TextView ) mUILayout.findViewById ( R.id.cameraStatusView );

        // Shows the loading indicator at start
        loadingDialogHandler.sendEmptyMessage ( LoadingDialogHandler.SHOW_LOADING_DIALOG );

        // Gets a reference to the Close Button
        mCloseButton = ( ImageButton ) mUILayout.findViewById ( R.id.cameraCloseButton );

        // Sets the Close Button functionality
        mCloseButton.setOnClickListener ( new View.OnClickListener ( )
        {
            public void onClick ( View v )
            {
                // Updates application status
                mScheduleInfoStatus = SCHEDULEINFO_NOT_DISPLAYED;

                loadingDialogHandler.sendEmptyMessage ( HIDE_LOADING_DIALOG );

                // Enters Scanning Mode
                enterScanningMode ( );
            }
        } );

        mInformationButton = ( ImageButton ) findViewById ( R.id.cameraInformationButton );
        mInformationButton.setOnClickListener ( new View.OnClickListener ( )
        {
            @Override
            public void onClick ( View view )
            {
                Intent informationIntent = new Intent ( mActivity, DetailedScheduleActivity.class );

                // Create Information Object that will be passed to the Intent
                // Need Instructor and Course for the Schedule being viewed. As well as Section #

                // Get Section Number from Active Rows
                String sectionNumber = activeRows.get ( activeRowsIndex ).data.get ( "SectionNumber" );
                informationIntent.putExtra ( SectionNumberKey, sectionNumber );

                // Get "Instructor" row from Table
                String instructorName = activeRows.get ( activeRowsIndex ).data.get ( "Instructor" );
                Cursor instructorCursor = mDb.rawQuery ( "SELECT * FROM Instructors WHERE InstructorName = ?", new String[] { instructorName } );
                ArrayList<Row> instructorRows = new Rows ( instructorCursor ).getRows ( );

                // Add to Extras
                if ( instructorRows.size ( ) > 0 )
                {
                    Row desiredRow = instructorRows.get ( 0 );
                    String[] instructorKeys = desiredRow.data.keySet ( ).toArray ( new String[instructorRows.size ( )] );
                    String[] instructorValues = ( String[] ) desiredRow.data.values ( ).toArray ( new String[instructorRows.size ( )] );

                    Log.d ( LOGTAG, "Instructor Keys : " + Arrays.toString ( instructorKeys ) );
                    Log.d ( LOGTAG, "Instructor Keys : " + Arrays.toString ( instructorValues ) );

                    informationIntent.putExtra ( InstructorKeys, instructorKeys );
                    informationIntent.putExtra ( InstructorValues, instructorValues );
                }

                // Get "Course" row from Table
                String department = activeRows.get ( activeRowsIndex ).data.get ( "Department" );
                String courseNumber = activeRows.get ( activeRowsIndex ).data.get ( "CourseNumber" );
                Cursor courseCursor = mDb.rawQuery ( "SELECT * FROM Courses WHERE Department = ? AND CourseNumber = ?", new String[] { department, courseNumber } );
                ArrayList<Row> courseRows = new Rows ( courseCursor ).getRows ( );

                // Add to Extras
                if ( courseRows.size ( ) > 0 )
                {
                    Row desiredRow = courseRows.get ( 0 );
                    String[] courseKeys = desiredRow.data.keySet ( ).toArray ( new String[instructorRows.size ( )] );
                    String[] courseValues = desiredRow.data.values ( ).toArray ( new String[instructorRows.size ( )] );

                    Log.d ( LOGTAG, "Course Keys : " + Arrays.toString ( courseKeys ) );
                    Log.d ( LOGTAG, "Course Keys : " + Arrays.toString ( courseValues ) );

                    informationIntent.putExtra ( CourseKeys, courseKeys );
                    informationIntent.putExtra ( CourseValues, courseValues );
                }

                startActivity ( informationIntent );
            }
        } );

        mNextButton = ( ImageButton ) findViewById ( R.id.cameraNextButton );
        mNextButton.setOnClickListener ( new View.OnClickListener ( )
        {
            @Override
            public void onClick ( View view )
            {
                if ( activeRowsIndex + 1 >= activeRows.size ( ) )
                {
                    activeRowsIndex = 0;
                }
                else
                {
                    ++activeRowsIndex;
                }

                Toast.makeText ( mActivity, "Updated", Toast.LENGTH_SHORT ).show ( );
                shouldUpdateTexture = true;
                updateProductTexture ( );
            }
        } );

        mPreviousButton = ( ImageButton ) findViewById ( R.id.cameraPreviousButton );
        mPreviousButton.setOnClickListener ( new View.OnClickListener ( )
        {
            @Override
            public void onClick ( View view )
            {
                if ( activeRowsIndex - 1 >= 0 )
                {
                    --activeRowsIndex;
                }
                else
                {
                    activeRowsIndex = activeRows.size ( ) - 1;
                }

                Toast.makeText ( mActivity, "Updated", Toast.LENGTH_SHORT ).show ( );
                shouldUpdateTexture = true;
                updateProductTexture ( );
            }
        } );

        // As default the 2D overlay and Status bar are hidden when application
        // starts
        hide2DOverlay ( );
        hideStatusBar ( );
    }

    // Initializes AR application components.
    private void initApplicationAR ( )
    {
        // Create OpenGL ES view
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha ( );

        // Initialize the GLView with proper flags
        mGlView = new ApplicationGLView ( this );
        mGlView.init ( translucent, depthSize, stencilSize );

        // Setup the Renderer of the GLView
        mRenderer = new SchedulesRenderer ( this, vuforiaAppSession );
        mRenderer.mActivity = this;
        mGlView.setRenderer ( mRenderer );

        // Sets the device scale density
        setDeviceDPIScaleFactor ( mdpiScaleIndicator );

        initStateVariables ( );
    }

    // Sets the Status Bar Text in a UI thread
    public void setStatusBarText ( String statusText )
    {
        mStatusBarText = statusText;
        statusBarHandler.sendEmptyMessage ( SHOW_STATUS_BAR );
    }

    // Hides the Status bar 2D Overlay in a UI thread
    public void hideStatusBar ( )
    {
        if ( mStatusBar.getVisibility ( ) == View.VISIBLE )
        {
            statusBarHandler.sendEmptyMessage ( HIDE_STATUS_BAR );
        }
    }

    // Shows the Status Bar 2D Overlay in a UI thread
    public void showStatusBar ( )
    {
        if ( mStatusBar.getVisibility ( ) == View.INVISIBLE )
        {
            statusBarHandler.sendEmptyMessage ( SHOW_STATUS_BAR );
        }
    }

    public void updateProductTexture ( )
    {
        createProductTexture ( currentTrackable );
    }

    // Updates a ScheduleOverlayView with the Schedule data specified in parameters
    private void updateProductView ( ScheduleOverlayView productView, Schedule schedule )
    {
        productView.setLocation ( schedule.getLocation ( ) );
        productView.setCourse ( schedule.getCourse ( ) );
        productView.setSchedule ( schedule.getSchedule ( ) );
        productView.setInstructor ( schedule.getInstructor ( ) );
    }

    public void enterContentMode ( )
    {
        // Updates state variables
        mScheduleInfoStatus = SCHEDULEINFO_IS_DISPLAYED;

        // Shows the 2D Overlay
        show2DOverlay ( );

        // Remember we are in content mode
        mRenderer.setScanningMode ( false );
    }

    // Hides the 2D overlay view
    private void enterScanningMode ( )
    {
        // Hides the 2D Overlay
        hide2DOverlay ( );

        mRenderer.setScanningMode ( true );

        // Updates state variables
        mRenderer.showAnimation3Dto2D ( false );
        mRenderer.isShowing2DOverlay ( false );
        mRenderer.setRenderState ( SchedulesRenderer.RS_SCANNING );
    }

    // Displays the 2D Schedule Overlay
    public void show2DOverlay ( )
    {
        // Sends the Message to the Handler in the UI thread
        overlay2DHandler.sendEmptyMessage ( SHOW_2D_OVERLAY );
    }

    // Hides the 2D Book Overlay
    public void hide2DOverlay ( )
    {
        // Sends the Message to the Handler in the UI thread
        overlay2DHandler.sendEmptyMessage ( HIDE_2D_OVERLAY );
    }

    public boolean onTouchEvent ( MotionEvent event )
    {
        // Process the Gestures
        return mGestureDetector.onTouchEvent ( event );
    }

    @Override
    public boolean doLoadTrackersData ( )
    {
        Log.d ( LOGTAG, "initSchedules" );

        TrackerManager trackerManager = TrackerManager.getInstance ( );
        ObjectTracker objectTracker = ( ObjectTracker ) trackerManager.getTracker ( ObjectTracker.getClassType ( ) );

        if ( objectTracker == null )
        {
            return false;
        }

        if ( mCurrentDataset == null )
        {
            mCurrentDataset = objectTracker.createDataSet ( );
        }

        if ( mCurrentDataset == null )
        {
            return false;
        }

        if ( !mCurrentDataset.load ( mDatasetStrings.get ( 0 ), STORAGE_TYPE.STORAGE_APPRESOURCE ) )
        {
            return false;
        }

        if ( !objectTracker.activateDataSet ( mCurrentDataset ) )
        {
            return false;
        }

        int numTrackables = mCurrentDataset.getNumTrackables ( );
        for ( int count = 0; count < numTrackables; count++ )
        {
            Trackable trackable = mCurrentDataset.getTrackable ( count );
            Log.d ( LOGTAG, "Added : " + trackable.getName ( ) );
        }

        return true;
    }

    @Override
    public boolean doUnloadTrackersData ( )
    {
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance ( );
        ObjectTracker objectTracker = ( ObjectTracker ) trackerManager.getTracker ( ObjectTracker.getClassType ( ) );

        if ( objectTracker == null )
        {
            return false;
        }

        if ( mCurrentDataset != null && mCurrentDataset.isActive ( ) )
        {
            if ( objectTracker.getActiveDataSet ( 0 ).equals ( mCurrentDataset ) && !objectTracker.deactivateDataSet ( mCurrentDataset ) )
            {
                result = false;
            }
            else if ( !objectTracker.destroyDataSet ( mCurrentDataset ) )
            {
                result = false;
            }

            mCurrentDataset = null;
        }

        return result;
    }

    @Override
    public void onInitARDone ( ApplicationException exception )
    {

        if ( exception == null )
        {
            initApplicationAR ( );

            // Now add the GL surface view. It is important that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video background is configured
            addContentView ( mGlView, new ViewGroup.LayoutParams ( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) );

            // Start the camera
            vuforiaAppSession.startAR ( CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT );

            mRenderer.setActive ( true );

            mUILayout.bringToFront ( );

            // Hides the Loading Dialog
            loadingDialogHandler.sendEmptyMessage ( HIDE_LOADING_DIALOG );

            mUILayout.setBackgroundColor ( Color.TRANSPARENT );

        }
        else
        {
            Log.e ( LOGTAG, exception.getString ( ) );
            showInitializationErrorMessage ( exception.getString ( ) );
        }
    }

    @Override
    public void onVuforiaResumed ( )
    {
        if ( mGlView != null )
        {
            mGlView.setVisibility ( View.VISIBLE );
            mGlView.onResume ( );
        }
    }

    @Override
    public void onVuforiaStarted ( )
    {
        mRenderer.updateRenderingPrimitives ( );

        // Set camera focus mode
        if ( !CameraDevice.getInstance ( ).setFocusMode ( CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO ) )
        {
            // If continuous autofocus mode fails, attempt to set to a different mode
            if ( !CameraDevice.getInstance ( ).setFocusMode ( CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO ) )
            {
                CameraDevice.getInstance ( ).setFocusMode ( CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL );
            }
        }

        showProgressIndicator ( false );
    }

    public void showProgressIndicator ( boolean show )
    {
        if ( loadingDialogHandler != null )
        {
            if ( show )
            {
                loadingDialogHandler.sendEmptyMessage ( LoadingDialogHandler.SHOW_LOADING_DIALOG );
            }
            else
            {
                loadingDialogHandler.sendEmptyMessage ( LoadingDialogHandler.HIDE_LOADING_DIALOG );
            }
        }
    }

    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage ( String message )
    {
        final String errorMessage = message;
        runOnUiThread ( new Runnable ( )
        {
            public void run ( )
            {
                if ( mErrorDialog != null )
                {
                    mErrorDialog.dismiss ( );
                }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder ( Schedules.this );
                builder.setMessage ( errorMessage );
                builder.setTitle ( getString ( R.string.INIT_ERROR ) );
                builder.setCancelable ( false );
                builder.setIcon ( 0 );
                builder.setPositiveButton ( "OK", new DialogInterface.OnClickListener ( )
                {
                    public void onClick ( DialogInterface dialog, int id )
                    {
                        finish ( );
                    }
                } );

                mErrorDialog = builder.create ( );
                mErrorDialog.show ( );
            }
        } );
    }

    private String stringForStatus ( int status )
    {
        switch ( status )
        {
            case TrackableResult.STATUS.DETECTED:
            {
                return "DETECTED";
            }
            case TrackableResult.STATUS.EXTENDED_TRACKED:
            {
                return "EXTENDED TRACKED";
            }
            case TrackableResult.STATUS.LIMITED:
            {
                return "LIMITED";
            }
            case TrackableResult.STATUS.NO_POSE:
            {
                return "NO POSE";
            }
            case TrackableResult.STATUS.TRACKED:
            {
                return "TRACKED";
            }
            default:
                return "DEFAULT";

        }
    }

    private String stringForStatusInfo ( int statusInfo )
    {
        switch ( statusInfo )
        {
            case TrackableResult.STATUS_INFO.EXCESSIVE_MOTION:
            {
                return "EXCESSIVE MOTION";
            }
            case TrackableResult.STATUS_INFO.INITIALIZING:
            {
                return "INITIALIZING";
            }
            case TrackableResult.STATUS_INFO.INSUFFICIENT_FEATURES:
            {
                return "INSUFFICIENT FEATURES";
            }
            case TrackableResult.STATUS_INFO.NORMAL:
            {
                return "NORMAL";
            }
            case TrackableResult.STATUS_INFO.UNKNOWN:
            {
                return "UNKNOWN";
            }
            default:
                return "DEFAULT";
        }
    }

    public Texture getTexture ()
    {
        if ( mTexture == null )
            createProductTexture ( currentTrackable );

        return mTexture;
    }

    private void createProductTexture ( Trackable trackable )
    {
        loadingDialogHandler.sendEmptyMessage ( SHOW_LOADING_DIALOG );

        try
        {
            mScheduleData = new Schedule ( );

            if ( !shouldUpdateTexture )
            {
                String[] buildingAndRoom = trackable.getName ( ).split ( "-" );
                if ( buildingAndRoom.length != 2 )
                {
                    return;
                }

                String building = buildingAndRoom[0];
                String room = buildingAndRoom[1];

                Cursor queryResults = mDb.rawQuery ( "SELECT * FROM Schedule WHERE Building = ? AND Room = ?", new String[] { building, room } );

                activeRows = new Rows ( queryResults ).getRows ( );

                String[] columnNames = queryResults.getColumnNames ( );
                Log.d ( LOGTAG, "Column Names : " + Arrays.toString ( columnNames ) );
            }

            if ( activeRows.size ( ) > 0 )
            {
                // First time creating Texture so pick the one closest to System time
                if ( mRenderer.getmProductTexture ( ) == null )
                {
                    int currentHour = Calendar.getInstance ( ).get ( Calendar.HOUR );
                    int approximateScheduleIndex = 0, hourDifferential = Integer.MAX_VALUE;
                    for ( Row row : activeRows )
                    {
                        int rowHour = Integer.parseInt ( row.data.get ( "StartTime" ).split ( ":" )[0] );
                        int difference = Math.abs ( currentHour - rowHour );
                        if ( difference < hourDifferential )
                        {
                            hourDifferential = difference;
                            approximateScheduleIndex = activeRows.indexOf ( row );
                        }
                    }

                    activeRowsIndex = approximateScheduleIndex;
                }

                mScheduleData.fillUsingRow ( activeRows.get ( activeRowsIndex ) );

                // Generates a View to display the schedule data
                ScheduleOverlayView productView = new ScheduleOverlayView ( Schedules.this );

                // Updates the view used as a 3d Texture
                updateProductView ( productView, mScheduleData );

                // Sets the layout params
                productView.setLayoutParams ( new ViewGroup.LayoutParams ( RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT ) );

                // Sets View measure - This size should be the same as the
                // texture generated to display the overlay in order for the
                // texture to be centered in screen
                productView.measure ( View.MeasureSpec.makeMeasureSpec ( mTextureSize, View.MeasureSpec.EXACTLY ), View.MeasureSpec.makeMeasureSpec ( mTextureSize, View.MeasureSpec.EXACTLY ) );

                // updates layout size
                productView.layout ( 0, 0, productView.getMeasuredWidth ( ), productView.getMeasuredHeight ( ) );

                // Draws the View into a Bitmap. Note we are allocating several
                // large memory buffers thus attempt to clear them as soon as
                // they are no longer required:
                Bitmap bitmap = Bitmap.createBitmap ( mTextureSize, mTextureSize, Bitmap.Config.ARGB_8888 );

                Canvas c = new Canvas ( bitmap );
                productView.draw ( c );
                System.gc ( );

                // Allocate int buffer for pixel conversion and copy pixels
                int width = bitmap.getWidth ( );
                int height = bitmap.getHeight ( );

                int[] data = new int[bitmap.getWidth ( ) * bitmap.getHeight ( )];
                bitmap.getPixels ( data, 0, bitmap.getWidth ( ), 0, 0, bitmap.getWidth ( ), bitmap.getHeight ( ) );
                bitmap.recycle ( );
                System.gc ( );

                // Generates the Texture from the int buffer
                mTexture = Texture.loadTextureFromIntBuffer ( data, width, height );
                mRenderer.setProductTexture ( mTexture );

                // Hides the loading dialog from a UI thread
                loadingDialogHandler.sendEmptyMessage ( HIDE_LOADING_DIALOG );

                mRenderer.setRenderState ( SchedulesRenderer.RS_TEXTURE_GENERATED );

                if ( shouldUpdateTexture )
                {
                    shouldUpdateTexture = false;
                }
            }
        }
        catch ( Exception exception )
        {
            Log.d ( LOGTAG, "Couldn't get schedule : " + exception );
        }
    }

    @Override
    public void onVuforiaUpdate ( State state )
    {
        int numberOfTrackableResults = state.getNumTrackableResults ( );
        for ( int index = 0; index < numberOfTrackableResults; ++index )
        {
            TrackableResult result = state.getTrackableResult ( index );
            Trackable trackable = result.getTrackable ( );

            String status = stringForStatus ( result.getStatus ( ) );
            String statusInfo = stringForStatusInfo ( result.getStatusInfo ( ) );

            Log.d ( LOGTAG, "Trackable NAME : " + trackable.getName ( ) );
            Log.d ( LOGTAG, "Status : " + status + " | Status Info : " + statusInfo );

            if ( !currentTrackableId.equals ( trackable.getName ( ) ) )
            {
                currentTrackable = trackable;
                currentTrackableId = trackable.getName ( );
                Log.d ( LOGTAG, "Initialize Tracker's Texture Here" );

                mRenderer.setRenderState ( SchedulesRenderer.RS_LOADING );
                createProductTexture ( currentTrackable );
            }
            else
            {
                Log.d ( LOGTAG, "Rendering is Normal" );
                mRenderer.setRenderState ( SchedulesRenderer.RS_NORMAL );
            }

            mRenderer.setFramesToSkipBeforeRenderingTransition ( 10 );
            mRenderer.showAnimation3Dto2D ( true );
            mRenderer.resetTrackingStarted ( );
            enterContentMode ( );
        }
    }

    @Override
    public boolean doInitTrackers ( )
    {
        TrackerManager trackerManager = TrackerManager.getInstance ( );
        Tracker tracker = trackerManager.initTracker ( ObjectTracker.getClassType ( ) );

        if ( tracker == null )
        {
            Log.e ( LOGTAG, "Tracker not initialized. Tracker already initialized or the camera is already started" );
            return false;
        }
        else
        {
            Log.i ( LOGTAG, "Tracker successfully initialized" );
        }

        return true;
    }

    @Override
    public boolean doStartTrackers ( )
    {
        // Indicate if the trackers were started correctly and start the tracker
        TrackerManager trackerManager = TrackerManager.getInstance ( );
        ObjectTracker objectTracker = ( ObjectTracker ) trackerManager.getTracker ( ObjectTracker.getClassType ( ) );

        if ( objectTracker != null )
        {
            objectTracker.start ( );
        }

        return true;
    }

    @Override
    public boolean doStopTrackers ( )
    {
        // Indicate if the trackers were stopped correctly
        Tracker objectTracker = TrackerManager.getInstance ( ).getTracker ( ObjectTracker.getClassType ( ) );

        if ( objectTracker != null )
        {
            objectTracker.stop ( );
        }
        else
        {
            return false;
        }

        return true;
    }

    @Override
    public boolean doDeinitTrackers ( )
    {
        // Indicate if the trackers were deinitialized correctly
        TrackerManager trackerManager = TrackerManager.getInstance ( );
        trackerManager.deinitTracker ( ObjectTracker.getClassType ( ) );

        return true;
    }

    // Crates a Handler to Show/Hide the status bar overlay from an UI Thread
    static class StatusBarHandler extends Handler
    {
        private final WeakReference<Schedules> mSchedules;

        StatusBarHandler ( Schedules schedules )
        {
            mSchedules = new WeakReference<> ( schedules );
        }

        public void handleMessage ( Message msg )
        {
            Schedules schedules = mSchedules.get ( );

            if ( schedules == null )
            {
                return;
            }

            if ( msg.what == SHOW_STATUS_BAR )
            {
                schedules.mStatusBar.setText ( schedules.mStatusBarText );
                schedules.mStatusBar.setVisibility ( View.VISIBLE );
            }
            else
            {
                schedules.mStatusBar.setVisibility ( View.INVISIBLE );
            }
        }
    }

    // Creates a handler to Show/Hide the UI Overlay from an UI thread
    static class Overlay2dHandler extends Handler
    {
        private final WeakReference<Schedules> mSchedules;

        Overlay2dHandler ( Schedules schedules )
        {
            mSchedules = new WeakReference<> ( schedules );
        }

        public void handleMessage ( Message message )
        {
            Schedules schedules = mSchedules.get ( );
            if ( schedules == null )
            {
                return;
            }

            if ( schedules.mCloseButton != null )
            {
                if ( message.what == SHOW_2D_OVERLAY )
                {
                    schedules.mCloseButton.setVisibility ( View.VISIBLE );
                    schedules.mInformationButton.setVisibility ( View.VISIBLE );
                    schedules.mNextButton.setVisibility ( View.VISIBLE );
                    schedules.mPreviousButton.setVisibility ( View.VISIBLE );
                }
                else
                {
                    schedules.mCloseButton.setVisibility ( View.INVISIBLE );
                    schedules.mInformationButton.setVisibility ( View.INVISIBLE );
                    schedules.mNextButton.setVisibility ( View.INVISIBLE );
                    schedules.mPreviousButton.setVisibility ( View.INVISIBLE );
                }
            }
        }
    }

    // Process Double Tap event for showing the Camera options menu
    private class GestureListener extends GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler ( );

        public boolean onDown ( MotionEvent e )
        {
            return true;
        }

        public boolean onSingleTapUp ( MotionEvent event )
        {

            // If the book info is not displayed it performs an Autofocus
            if ( mScheduleInfoStatus == SCHEDULEINFO_NOT_DISPLAYED )
            {
                boolean result = CameraDevice.getInstance ( ).setFocusMode ( CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO );
                if ( !result )
                {
                    Log.e ( "SingleTapUp", "Unable to trigger focus" );
                }

                // Generates a Handler to trigger continuous auto-focus after 1 second
                autofocusHandler.postDelayed ( new Runnable ( )
                {
                    public void run ( )
                    {
                        final boolean autofocusResult = CameraDevice.getInstance ( ).setFocusMode ( CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO );

                        if ( !autofocusResult )
                        {
                            Log.e ( "SingleTapUp", "Unable to re-enable continuous auto-focus" );
                        }
                    }
                }, 1000L );
            }

            return true;
        }
    }

}
