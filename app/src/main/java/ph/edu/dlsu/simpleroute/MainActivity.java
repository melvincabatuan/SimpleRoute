package ph.edu.dlsu.simpleroute;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    private static final int RIGHT = 0;
    private static final int LEFT = 1;
    private static final int TOP = 2;
    private static final int BOTTOM = 3;

    private Mat m;
    private List<Point> centers;

    int[] velasco = {
            R.drawable.velasco_1,
            R.drawable.velasco_2,
            R.drawable.velasco_3,
            R.drawable.velasco_4,
            R.drawable.velasco_5};

    int floorIndex;

    // Bindings
    @Bind(R.id.imageView)
    ImageView iv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        loadMap();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS ) {
                // OpenCV Successfully Loaded!!!
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    public void loadMap() {
        Picasso.with(this).load(velasco[floorIndex]).into(iv);
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0,this, mLoaderCallback);
    }


    @Override
    public void onStop() {
        super.onStop();
        if (m != null){
            m.release();
        }
    }


    // Handle buttons
    public void onClickNext(View view){
        floorIndex++;
        floorIndex = floorIndex % velasco.length;
        loadMap();
        iv.postInvalidate();
    }

    public void onClickRoute(View view){

        // Acquire drawable into bitmap
        Bitmap floorMap = BitmapFactory.decodeResource(getResources(), velasco[floorIndex]);

        // Initialize opencv Mat for processing
        m = new Mat();
        Mat hsv = new Mat();
        Mat mask = new Mat();

        // Convert floor map bitmap to opencv Mat
        Utils.bitmapToMat(floorMap,m);

        Imgproc.cvtColor(m, hsv, Imgproc.COLOR_RGB2HSV);
        Core.inRange(hsv, new Scalar(0, 70, 50), new Scalar(10, 255, 255), mask);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Centers
        centers = new ArrayList<>();

        for( int i = 0; i < contours.size(); i++ )
        {
            Rect rect = Imgproc.boundingRect(contours.get(i));
            Imgproc.rectangle(m, new Point(rect.x,rect.y), new Point(rect.x+rect.width,rect.y+rect.height),new Scalar(255,0,0,255), 8); // RGBA
            centers.add(new Point(rect.x + rect.width/2.0,rect.y + rect.height/2.0));
            Imgproc.drawContours( m, contours, i, new Scalar(255,255,255,255), -1 ); // RGBA
        }

        drawRouteA();

        // Convert back to bitmap:
        Bitmap bm = Bitmap.createBitmap(m.cols(), m.rows(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m, bm);
        iv.setImageBitmap(bm);
        iv.postInvalidate();
    }

    private void drawRouteA() {

        // Starting point is the lowest point; highest y
        Point start = centers.get(0);
        double max_y = centers.get(0).y;

        for (int i = 1; i < centers.size(); i++){
            if (centers.get(i).y > max_y){
                max_y = centers.get(i).y;
                start = centers.get(i);
            }
        }

        start = move(start, RIGHT); // start.x = 1227.5, start.y = 591.5

        start = move(start, TOP);   // start.x = 1314.0, start.y = 588.0

        start = move(start, LEFT);

        // move(start, LEFT);

    }


    private Point move(Point current, int direction){

        Log.d("Main", "current.x = " + current.x + ", current.y = " + current.y);

        removePoint(current);

        List<Point> candidate = new ArrayList<>();
        Point next = current;

        double distance;
        double threshold = m.rows()/2;
        double epsilon = 40;  // Little change in height or width

        for (int i = 0; i < centers.size(); i++){
            Point tempPoint = centers.get(i);
            distance = euclidean(current, tempPoint);
            if ( (distance > 0) && (distance < threshold)){ // not equal to current
                switch(direction){
                    case RIGHT: // Slight difference in height and to the right
                       if ((Math.abs(tempPoint.y - current.y) < epsilon) && ((tempPoint.x - current.x) > 0)){
                           candidate.add(tempPoint);
                       }
                    case LEFT: // Slight difference in height and to the left
                        if ((Math.abs(tempPoint.y - current.y) < epsilon) && ((tempPoint.x - current.x) < 0)){
                            candidate.add(tempPoint);
                        }
                    case TOP: // Slight difference in width and to the top
                        if ((Math.abs(tempPoint.x - current.x) < epsilon) && ((tempPoint.y - current.y) < 0)){
                            candidate.add(tempPoint);
                        }
                    case BOTTOM: // Slight difference in width and to the bottom
                        if ((Math.abs(tempPoint.x - current.x) < epsilon) && ((tempPoint.y - current.y) > 0)){
                            candidate.add(tempPoint);
                        }
                }
            }

        }



        if(!candidate.isEmpty()){
            Point bestCand = current;
            double bestDist = Double.MAX_VALUE;
            double tempDist;
            for (int j = 0; j < candidate.size(); j++){
                tempDist = euclidean(current, candidate.get(j));
                if( tempDist < bestDist){
                    bestCand = candidate.get(j);
                    bestDist = tempDist;
                    Log.d("Main", "centers.sizes = "  + centers.size());
                    Log.d("Main", "candidate.size = "  + candidate.size());
                    Log.d("Main", "bestDist =  " + bestDist);
                }
            }
            next = bestCand;
            candidate.clear();
        }

        Log.d("Main", "next.x = " + next.x + ", next.y = " + next.y);
        Imgproc.line(m,current,next,new Scalar(255,255,255,255),24);
        return next;
    }

    private void removePoint(Point current) {
        for (int i = 0; i < centers.size(); i++){
            Point tmp = centers.get(i);
            if (euclidean(tmp, current) < 5.0){
                centers.remove(i);
            }
        }
    }

//    private double manhattan(Point first, Point second){
//        double dx = Math.abs(second.x - first.x);
//        double dy = Math.abs(second.y - first.y);
//        return (dx + dy);
//    }

    private double euclidean(Point first, Point second){
        double dx =  (second.x - first.x);
        double dy =  (second.y - first.y);
        return Math.sqrt(dx*dx + dy*dy);
    }
}
