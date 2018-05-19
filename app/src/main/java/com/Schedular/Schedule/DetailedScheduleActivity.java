package com.Schedular.Schedule;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Switch;
import android.widget.TextView;

import com.Schedular.R;

import java.io.IOException;
import java.io.InputStream;

public class DetailedScheduleActivity extends Activity
{
    @Override
    protected void onCreate ( Bundle savedInstanceState )
    {
        super.onCreate ( savedInstanceState );
        setContentView ( R.layout.activity_detailed_schedule );

        // Get Intent
        Intent intent = getIntent ();

        // Get Strings Arrays from Intent
        String sectionNumber = intent.getStringExtra ( Schedules.SectionNumberKey );
        String[] instructorKeys = intent.getStringArrayExtra ( Schedules.InstructorKeys );
        String[] instructorValues = intent.getStringArrayExtra ( Schedules.InstructorValues );
        String[] courseKeys = intent.getStringArrayExtra ( Schedules.CourseKeys );
        String[] courseValues = intent.getStringArrayExtra ( Schedules.CourseValues );

        if ( instructorKeys == null || instructorValues == null || courseKeys == null || courseValues == null )
            return;

        if ( instructorKeys.length != instructorValues.length && courseKeys.length != courseValues.length )
            return;

        // Update Views
        String courseNumber = ":D: :CN: - " + sectionNumber;
        String courseName = "";
        String courseDescription = "";

        for ( int index = 0; index < courseKeys.length; ++index )
        {
            switch ( courseKeys[index].toUpperCase () )
            {
                case "DEPARTMENT":
                {
                    courseNumber = courseNumber.replaceFirst ( ":D:", courseValues[index] );
                    break;
                }
                case "COURSENUMBER":
                {
                    courseNumber = courseNumber.replaceFirst ( ":CN:", courseValues[index] );
                    break;
                }
                case "COURSENAME":
                {
                    courseName = courseValues[index];
                    break;
                }
                case "COURSEDESCRIPTION":
                {
                    courseDescription = courseValues[index];
                    break;
                }
                default:
                    break;
            }
        }

        ( ( TextView ) findViewById ( R.id.detailedCourseNumberTextView ) ).setText ( courseNumber );
        ( ( TextView ) findViewById ( R.id.detailedCourseNameTextView ) ).setText ( courseName );
        ( ( TextView ) findViewById ( R.id.detailedCourseDescriptionTextView ) ).setText ( courseDescription );

        String instructor = "";
        String officeBuilding = "";
        String officeRoom = "";
        String email = "";

        for ( int index = 0; index < instructorKeys.length; ++index )
        {
            switch ( instructorKeys[index].toUpperCase () )
            {
                case "INSTRUCTORNAME":
                {
                    instructor = instructorValues[index];

                    try
                    {
                        // Get list of Faculty Images, try to find the one that matches, and use
                        // that Image for the Image View
                        for ( String instructorName : getAssets ().list ( "Faculty" ) )
                        {
                            if ( instructorName.contains ( instructor ) )
                            {
                                InputStream inputstream = getAssets().open("Faculty/" + instructorName );
                                Drawable instructorDrawable = Drawable.createFromStream ( inputstream, null );
                                ImageView instructorImageView = ( ImageView ) findViewById ( R.id.detailedInstructorImageView );
                                instructorImageView.setImageDrawable ( instructorDrawable );
                                break;
                            }
                        }
                    }
                    catch ( IOException exception )
                    {
                        exception.printStackTrace ();
                    }

                    break;
                }
                case "OFFICEBUILDING":
                {
                    officeBuilding = instructorValues[index];
                    break;
                }
                case "OFFICEROOM":
                {
                    officeRoom = instructorValues[index];
                    break;
                }
                case "EMAIL":
                {
                    email = instructorValues[index];
                    break;
                }
                default:
                    break;
            }
        }

        ( ( TextView ) findViewById ( R.id.detailedInstructorTextView ) ).setText ( instructor );
        ( ( TextView ) findViewById ( R.id.detailedOfficeBuildingTextView ) ).setText ( officeBuilding );
        ( ( TextView ) findViewById ( R.id.detailedOfficeRoomTextView ) ).setText ( officeRoom );
        ( ( TextView ) findViewById ( R.id.detailedOfficeHoursTextView ) ).setText ( "TBD" );
        ( ( TextView ) findViewById ( R.id.detailedEmailTextView ) ).setText ( email );
    }

    public void showRatingOnClick ( View view )
    {
        if ( view.equals ( findViewById ( R.id.detailedRatingSwitch ) ) )
        {
            Switch ratingSwitch = ( Switch ) view;
            RatingBar ratingBar = ( RatingBar ) findViewById ( R.id.detailedRatingBar );

            if ( ratingSwitch.isChecked () )
            {
                ratingBar.setRating ( ( float ) Math.random () * 10.0f / 2.0f );
            }
            else
            {
                ratingBar.setRating ( 0.0f );
            }
        }
    }

    public void doneButtonOnClick ( View view )
    {
        if ( view.equals ( findViewById ( R.id.detailedDoneButton ) ) )
        {
            this.finish ();
        }
    }
}
