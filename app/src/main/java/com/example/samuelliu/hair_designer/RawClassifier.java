package com.example.samuelliu.hair_designer;
        import android.content.Context;
        import android.util.Log;

        import org.opencv.core.*;
        import org.opencv.core.Core;
        import org.opencv.imgcodecs.Imgcodecs;
        import org.opencv.imgproc.Imgproc;
        import org.opencv.objdetect.CascadeClassifier;

        import java.io.File;
        import java.io.FileOutputStream;
        import java.io.IOException;
        import java.io.InputStream;

public class RawClassifier {
    private Mat my_image = null;
    private String filename = null;
    private Rect my_facerect = null;
    private ColorRecg_YCrCb face_color_whithin = null;
    private String classifier_path = "./haarcascade_frontalface_default.xml";
    public RawClassifier(String in_file){
        filename = in_file;
        System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
        my_image = Imgcodecs.imread(filename);
        my_facerect = null;
    }
    public RawClassifier(Mat in_image){
        filename = "UNUSED";
        //System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
        my_image = in_image.clone();
        my_facerect = null;
    }
    private static final String TAG = "OCVSample::Activity";

    //This function is responsible for detecting the rough range of the face.
    public Rect face_rect(){
        if(my_image == null){
            System.out.println("not initialized");
            return null;
        }
        if(my_facerect != null)
            return my_facerect;
        //start the detecting
        DetectionBasedTracker mNativeDetector;
        File mCascadeFile;
        CascadeClassifier mJavaDetector;
        System.out.println("\nRunning DetectFaceDemo");
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
            File cascadeDir = getDir("cascade" , Context.MODE_PRIVATE);
            mCascadeFile = new File(cascadeDir , "haarcascade_frontalface_default.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ( (bytesRead = is.read(buffer)) != -1 ){
                os.write(buffer , 0 , bytesRead);
            }
            is.close();
            os.close();

            mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            if ( mJavaDetector.empty() ) {
                Log.e(TAG, "Failed to load cascade classifier");
                mJavaDetector = null;
            }else
                Log.i(TAG , "LOaded cascade classifier from" + mCascadeFile.getAbsolutePath());

            mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath() , 0);

            cascadeDir.delete( );
        } catch (IOException e){
            e.printStackTrace();
            Log.e(TAG , "Failed to load cascade. Exception thrown: " + e);
        }
        CascadeClassifier face_rect_dtect = new CascadeClassifier(classifier_path);
        MatOfRect face_rect_out = new MatOfRect();
        face_rect_dtect.detectMultiScale(my_image, face_rect_out);
        System.out.println(String.format("Detected %s faces", face_rect_out.toArray().length));
        //we have a matrix of rects representing the position of faces
        //choose the most obvious one
        Rect final_face = null;
        Mat image = my_image.clone();
        for (Rect rect: face_rect_out.toArray()){
            if(final_face == null || final_face.width < rect.width)
                final_face = rect;
            Imgproc.rectangle(image, new Point(rect.x, rect.y),
                    new Point(rect.x + rect.width, rect.y + rect.height),
                    new Scalar(0, 255, 0));
        }
        System.out.println();
        System.out.println(final_face.x + " " + (final_face.x+final_face.width) + " " + final_face.y + " " + (final_face.y+final_face.height));
        face_color_whithin = new ColorRecg_YCrCb(
                my_image.submat(final_face.y, final_face.y+final_face.height,
                        final_face.x, final_face.x+final_face.width).clone()
        );
//        String filename = "faceDetection_rect.png";
//        System.out.println(String.format("Writing %s", filename));
//        Imgcodecs.imwrite(filename, image);
        my_facerect = final_face;
        return final_face;
    }

    //wrapper for the face_rect to accommodate code in imagerecognize.java
    public int[] getloc(){
        Rect rect = face_rect();
        System.out.println( "processing");
        return new int[]{rect.x , rect.y , rect.width , rect.height};
    }

    //TODO: change the return type of this function
    public void face_contour_byrect(){
        //first create helper vars and get the mask
        Rect face_rawrange = face_rect();
        //face_rawrange.height*=1.1;
        //creating the mask which are all background first. Then using the rect to define the foreground.
        Mat mask = new Mat();
        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        //create matrix full of 3, which indicate the foreground.
        Mat source = new Mat(my_image.height(), my_image.width(), CvType.CV_8U, new Scalar(3));
        Imgproc.grabCut(my_image, mask, face_rawrange, bgdModel, fgdModel, 8, Imgproc.GC_INIT_WITH_RECT);

        Core.compare(mask, source, mask, Core.CMP_EQ);
        Mat foreground = new Mat(my_image.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        my_image.copyTo(foreground, mask);

//        String filename = "faceDetection_contour.png";
//        System.out.println(String.format("Writing %s", filename));
//        Imgcodecs.imwrite(filename, foreground);
    }

    public Mat face_contour_bymask_new(){
        face_rect();
        //creating the final mask for output.
        Mat mask = new Mat(my_image.rows(), my_image.cols(), CvType.CV_8U);
        mask.setTo(new Scalar(Imgproc.GC_PR_BGD));
        //this is the mask specifically for the face area.
        Mat mask_in = face_color_whithin.get_skin_mask();
        Mat tmp_mask_frt = new Mat(my_facerect.height, my_facerect.width, CvType.CV_8U);
        tmp_mask_frt.setTo(new Scalar(Imgproc.GC_PR_BGD));
        //System.out.println(my_facerect.width);
        //System.out.println(mask_in);
        tmp_mask_frt.setTo(new Scalar(Imgproc.GC_FGD), mask_in);
        Mat mask_sub_ptr = mask.colRange(my_facerect.x, my_facerect.x+my_facerect.width).
                rowRange(my_facerect.y, my_facerect.y+my_facerect.height);
        tmp_mask_frt.copyTo(mask_sub_ptr);

        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        //create matrix full of 3, which indicate the foreground.
        Imgproc.grabCut(my_image, mask, my_facerect, bgdModel, fgdModel, 8, Imgproc.GC_INIT_WITH_MASK);
        Mat source = new Mat(my_image.height(), my_image.width(), CvType.CV_8U, new Scalar(Imgproc.GC_PR_FGD));
        mask_sub_ptr.setTo(new Scalar(Imgproc.GC_PR_FGD), mask_in);

        Core.compare(mask, source, mask, Core.CMP_EQ);
        Mat foreground = new Mat(my_image.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        my_image.copyTo(foreground, mask);
        return foreground;
//        String filename = "faceDetection_contour2.png";
//        System.out.println(String.format("Writing %s", filename));
//        Imgcodecs.imwrite(filename, foreground);
    }

    //TODO: if we can get some input.....
    public void face_contour_bymask(short[][] matrix){
        //creating the mask which are all background first. Then using the rect to define the foreground.
        Rect face_rawrange = face_rect();
        Mat mask = new Mat(my_image.rows(), my_image.cols(), CvType.CV_8U);
        mask.setTo(new Scalar(Imgproc.GC_PR_BGD));
        Mat tmp_mask_frt = new Mat(face_rawrange.height, face_rawrange.width, CvType.CV_8U);
        tmp_mask_frt.setTo(new Scalar(Imgproc.GC_PR_BGD));
        for(int i=0; i<matrix.length; i++){
            for(int j=0; j<matrix[0].length; j++){
                tmp_mask_frt.put(i, j, matrix[i][j]);
            }
        }
        Mat mask_sub_ptr = mask.colRange(face_rawrange.x, face_rawrange.x+face_rawrange.width).
                rowRange(face_rawrange.y, face_rawrange.y+face_rawrange.height);
        tmp_mask_frt.copyTo(mask_sub_ptr);

		/*Imgproc.ellipse(mask, new Point(face_rawrange.x+face_rawrange.width/2, face_rawrange.y+face_rawrange.height/2),
				new Size(face_rawrange.y, face_rawrange.y/1.3), 43.0, 0.0, 360.0, new Scalar(Imgproc.GC_PR_FGD), -1);*/

        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        double[] old_val = {Imgproc.GC_FGD};
        double new_val = Imgproc.GC_PR_FGD;
        //create matrix full of 3, which indicate the foreground.
        Imgproc.grabCut(my_image, mask, face_rawrange, bgdModel, fgdModel, 8, Imgproc.GC_INIT_WITH_MASK);
        Mat source = new Mat(my_image.height(), my_image.width(), CvType.CV_8U, new Scalar(Imgproc.GC_PR_FGD));
        for(int i=face_rawrange.x; i<face_rawrange.x+face_rawrange.width; i++)
            for(int j=face_rawrange.y; j<face_rawrange.y + face_rawrange.height; j++)
                if(mask.get(j, i)[0] == old_val[0])
                    mask.put(j, i, new_val);


        Core.compare(mask, source, mask, Core.CMP_EQ);
        Mat foreground = new Mat(my_image.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        my_image.copyTo(foreground, mask);

//        String filename = "faceDetection_contour2.png";
//        System.out.println(String.format("Writing %s", filename));
//        Imgcodecs.imwrite(filename, foreground);
    }
}
