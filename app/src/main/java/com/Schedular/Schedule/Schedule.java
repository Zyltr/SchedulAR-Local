package com.Schedular.Schedule;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Schedule
{
    private static SimpleDateFormat militaryTimeSimpleDateFormatter = new SimpleDateFormat("H:mm");
    private static SimpleDateFormat standardTimeSimpleDateFormatter = new SimpleDateFormat("h:mm a");

    private String location;
    private String course;
    private String schedule;
    private String instructor;

    public Schedule ( ) { }

    public String getLocation ( ) { return location; }

    public String getCourse ( )
    {
        return course;
    }

    public String getSchedule ( )
    {
        return schedule;
    }

    public String getInstructor ( )
    {
        return instructor;
    }

    public void fillUsingRow ( Row row )
    {
        location = ":B: :R:";
        course = ":D: :CN: - :SN:";
        schedule = ":D: from :ST: to :ET:";
        instructor = ":I:";

        for ( String key : row.data.keySet ( ) )
        {
            String value = row.data.get ( key );

            switch ( key.toUpperCase ( ) )
            {
                case "DEPARTMENT":
                {
                    course = course.replaceFirst ( ":D:", value );
                    break;
                }
                case "COURSENUMBER":
                {
                    course = course.replaceFirst ( ":CN:", value );
                    break;
                }
                case "SECTIONNUMBER":
                {
                    course = course.replaceFirst ( ":SN:", value );
                    break;
                }
                case "DAYS":
                {
                    schedule = schedule.replaceFirst ( ":D:", value );
                    break;
                }
                case "STARTTIME":
                {
                    try
                    {
                        Date date = militaryTimeSimpleDateFormatter.parse ( value );
                        schedule = schedule.replaceFirst ( ":ST:", standardTimeSimpleDateFormatter.format ( date ) );
                    }
                    catch ( ParseException parseException )
                    {
                        parseException.printStackTrace ();
                    }

                    break;
                }
                case "ENDTIME":
                {
                    try
                    {
                        Date date = militaryTimeSimpleDateFormatter.parse ( value );
                        schedule = schedule.replaceFirst ( ":ET:", standardTimeSimpleDateFormatter.format ( date )  );
                    }
                    catch ( ParseException parseException )
                    {
                        parseException.printStackTrace ();
                    }

                    break;
                }
                case "BUILDING":
                {
                    location = location.replaceFirst ( ":B:", value );
                    break;
                }
                case "ROOM":
                {
                    location = location.replaceFirst ( ":R:", value );
                    break;
                }
                case "INSTRUCTOR":
                {
                    instructor = instructor.replace ( ":I:", value );
                    break;
                }
                default:
                    break;
            }
        }
    }
}
