#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/contrib/detection_based_tracker.hpp>
#include <opencv2/objdetect/objdetect.hpp>

#include "asmfitting.h"
#include "AAM_IC.h"

#include <string>
#include <vector>

#include <android/log.h>
#include <jni.h>

using namespace std;
using namespace cv;

#define LOG_TAG "ASMLIBRARY"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define BEGINT()	double t = (double)cvGetTickCount();
#define ENDT(exp)	t = ((double)cvGetTickCount() -  t )/  (cvGetTickFrequency()*1000.);	\
					LOGD(exp " time cost: %.2f millisec\n", t);

asmfitting fit_asm;
AAM_IC aam;
DetectionBasedTracker *track = NULL;
CascadeClassifier face_cascade;
IplImage* avatarImage = NULL;
AAM_Shape avatarShape;

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeReadAAMModel
(JNIEnv * jenv, jclass, jstring jFileName)
{
    LOGD("nativeReadAAMModel enter");
    const char* filename = jenv->GetStringUTFChars(jFileName, NULL);
    jboolean result = false;

    try
    {
	if(aam.ReadModel(filename) == true)
		result = true;

    }
    catch (...)
    {
        LOGD("nativeReadAAMModel caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, "Unknown exception in JNI code");
    }

    LOGD("nativeReadAAMModel %s exit %d", filename, result);
    return result;
}

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeLoadAvatar
(JNIEnv * jenv, jclass, jstring jImageName, jstring jPTSName)
{
    LOGD("nativeLoadAvatar enter");
    const char* image = jenv->GetStringUTFChars(jImageName, NULL);
    const char* pts = jenv->GetStringUTFChars(jPTSName, NULL);

    avatarImage = cvLoadImage(image, 1);
    if(avatarImage == NULL) 
    	return false;

    avatarShape.ReadPTS(pts);

    return true;
}

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeReadModel
(JNIEnv * jenv, jclass, jstring jFileName)
{
    LOGD("nativeReadModel enter");
    const char* filename = jenv->GetStringUTFChars(jFileName, NULL);
    jboolean result = false;

    try
    {
	if(fit_asm.Read(filename) == true)
		result = true;

    }
    catch (...)
    {
        LOGD("nativeReadModel caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, "Unknown exception in JNI code");
    }

    LOGD("nativeReadModel %s exit %d", filename, result);
    return result;
}

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeInitCascadeDetector
(JNIEnv * jenv, jclass, jstring jFileName)
{
    const char* cascade_name = jenv->GetStringUTFChars(jFileName, NULL);
    LOGD("nativeInitCascadeDetector %s enter", cascade_name);

    if(!face_cascade.load(cascade_name))
		return false;

    LOGD("nativeInitCascadeDetector exit");
    return true;
}

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeInitFastCascadeDetector
(JNIEnv * jenv, jclass, jstring jFileName)
{
    const char* cascade_name = jenv->GetStringUTFChars(jFileName, NULL);
    LOGD("nativeInitFastCascadeDetector %s enter", cascade_name);

    DetectionBasedTracker::Parameters DetectorParams;
    DetectorParams.minObjectSize = 45;
    track = new DetectionBasedTracker(cascade_name, DetectorParams);

    if(track == NULL)	return false;

    DetectorParams = track->getParameters();
    DetectorParams.minObjectSize = 64;
    track->setParameters(DetectorParams);

    track->run();

    LOGD("nativeInitFastCascadeDetector exit");
    return true;
}

JNIEXPORT void JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeDestroyCascadeDetector
(JNIEnv * jenv, jclass)
{
    LOGD("nativeDestroyCascadeDetector enter");

    LOGD("nativeDestroyCascadeDetector exit");
}

JNIEXPORT void JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeDestroyFastCascadeDetector
(JNIEnv * jenv, jclass)
{
    LOGD("nativeDestroyFastCascadeDetector enter");

    if(track){
    	track->stop();
    	delete track;
    }

    LOGD("nativeDestroyFastCascadeDetector exit");
}

inline void shape_to_Mat(asm_shape shapes[], int nShape, Mat& mat)
{
	mat = Mat(nShape, shapes[0].NPoints()*2, CV_64FC1); 

	for(int i = 0; i < nShape; i++)
	{
		double *pt = mat.ptr<double>(i);  
		for(int j = 0; j < mat.cols/2; j++)
		{
			pt[2*j] = shapes[i][j].x;
			pt[2*j+1] = shapes[i][j].y;		
		}
	}
}

inline void Mat_to_shape(asm_shape shapes[], int nShape, Mat& mat)
{
	for(int i = 0; i < nShape; i++)
	{
		double *pt = mat.ptr<double>(i);  
		shapes[i].Resize(mat.cols/2);
		for(int j = 0; j < mat.cols/2; j++)
		{
			shapes[i][j].x = pt[2*j];
			shapes[i][j].y = pt[2*j+1];
		}
	}
}

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeFastDetectAll
(JNIEnv * jenv, jclass, jlong imageGray, jlong faces)
{
	if(!track)	return false;

	BEGINT();

	vector<Rect> RectFaces;
	try{
		Mat image = *(Mat*)imageGray;
		LOGD("image: (%d, %d)", image.cols, image.rows);
		track->process(image);
		track->getObjects(RectFaces);
	}
	catch(cv::Exception& e)
	{
		LOGD("nativeFastDetectAll caught cv::Exception: %s", e.what());
		jclass je = jenv->FindClass("org/opencv/core/CvException");
		if(!je)
			je = jenv->FindClass("java/lang/Exception");
		jenv->ThrowNew(je, e.what());
	}
	catch (...)
	{
		LOGD("nativeFastDetectAll caught unknown exception");
		jclass je = jenv->FindClass("java/lang/Exception");
		jenv->ThrowNew(je, "Unknown exception in JNI code");
	}

	int nFaces = RectFaces.size();
	if(nFaces <= 0){
		ENDT("FastCascadeDetector CANNOT detect any face");
		return false;
	}

	LOGD("FastCascadeDetector found %d faces", nFaces);

	asm_shape* detshapes = new asm_shape[nFaces];
	for(int i = 0; i < nFaces; i++){
		Rect r = RectFaces[i];
		detshapes[i].Resize(2);
		detshapes[i][0].x = r.x;
		detshapes[i][0].y = r.y;
		detshapes[i][1].x = r.x+r.width;
		detshapes[i][1].y = r.y+r.height;
	}

	asm_shape* shapes = new asm_shape[nFaces];
	for(int i = 0; i < nFaces; i++)
	{
		InitShapeFromDetBox(shapes[i], detshapes[i], fit_asm.GetMappingDetShape(), fit_asm.GetMeanFaceWidth());
	}

	shape_to_Mat(shapes, nFaces, *((Mat*)faces));

	delete []detshapes;
	delete []shapes;

	ENDT("FastCascadeDetector detect");

	return true;
}

inline bool detect_all_faces(std::vector<asm_shape>& det_shapes, const Mat& image)
{
    std::vector<Rect> faces;
    Mat image_small(image.rows/2, image.cols/2, CV_8UC1);
    resize( image, image_small, image_small.size(), 0, 0, INTER_LINEAR );

    face_cascade.detectMultiScale(image_small, faces, 1.1, 2, CV_HAAR_SCALE_IMAGE, Size(30, 30) );
	if(faces.size() == 0)
		return false;

	det_shapes.resize(faces.size());
	for( int i = 0; i < faces.size(); i++ )
    {
        det_shapes[i].Resize(2);
        det_shapes[i][0].x = faces[i].x;
        det_shapes[i][0].y = faces[i].y;
        det_shapes[i][1].x = faces[i].x + faces[i].width;
        det_shapes[i][1].y = faces[i].y + faces[i].height;
        det_shapes[i] *= 2;
    }
    return true;
}

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeDetectAll
(JNIEnv * jenv, jclass, jlong imageGray, jlong faces)
{
	Mat image = *((Mat*)imageGray);
	BEGINT();

    std::vector<asm_shape> det_shapes;
	if(!detect_all_faces(det_shapes, image)){
		ENDT("CascadeDetector CANNOT detect any face");
		return false;
	}
    LOGD("CascadeDetector found %d faces", det_shapes.size());
	asm_shape* shapes = new asm_shape[det_shapes.size()];
	for( int i = 0; i < det_shapes.size(); i++ )
    {
        InitShapeFromDetBox(shapes[i], det_shapes[i], fit_asm.GetMappingDetShape(), fit_asm.GetMeanFaceWidth());
    }

	shape_to_Mat(shapes, det_shapes.size(), *((Mat*)faces));
	delete []shapes;
	
	ENDT("CascadeDetector detect");

	return true;
}

JNIEXPORT void JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeInitShape(JNIEnv * jenv, jclass, jlong faces)
{
	Mat faces1 = *((Mat*)faces);
	int nFaces = faces1.rows;
	asm_shape* detshapes = new asm_shape[nFaces];
	asm_shape* shapes = new asm_shape[nFaces];

	Mat_to_shape(detshapes, nFaces, faces1);

	for(int i = 0; i < nFaces; i++)
	{
		InitShapeFromDetBox(shapes[i], detshapes[i], fit_asm.GetMappingDetShape(), fit_asm.GetMeanFaceWidth());
	}

	shape_to_Mat(shapes, nFaces, *((Mat*)faces));

	delete []detshapes;
	delete []shapes;
}

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeDetectOne
(JNIEnv * jenv, jclass, jlong imageGray, jlong face)
{
    BEGINT();
	Mat image = *((Mat*)imageGray);
    std::vector<asm_shape> det_shapes;
    if(!detect_all_faces(det_shapes, image)){
    	ENDT("CascadeDetector CANNOT detect any face");
    	return false;
    }

    int iSelectedFace = 0;
    double x0 = image.cols/2., y0 = image.rows/2.;
    double x, y, d, D = 1e307;
    for (int i = 0; i < det_shapes.size(); i++)
    {
        x = (det_shapes[i][0].x + det_shapes[i][1].x) / 2.;
        y = (det_shapes[i][0].y + det_shapes[i][1].y) / 2.;
        d = sqrt((x-x0)*(x-x0) + (y-y0)*(y-y0));
        if (d < D)
        {
            D = d;
            iSelectedFace = i;
        }
    }
    asm_shape shape = det_shapes[iSelectedFace],detshape;
	InitShapeFromDetBox(shape, detshape, fit_asm.GetMappingDetShape(), fit_asm.GetMeanFaceWidth());

	shape_to_Mat(&shape, 1, *((Mat*)face));
	
	ENDT("CascadeDetector detects central face");

	return true;
}


JNIEXPORT void JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeFitting
(JNIEnv * jenv, jclass, jlong imageGray, jlong shapes0, jlong n_iteration)
{
	IplImage image = *(Mat*)imageGray;
	Mat shapes1 = *(Mat*)shapes0;	
	int nFaces = shapes1.rows;	
	asm_shape* shapes = new asm_shape[nFaces];
	
	BEGINT();

	Mat_to_shape(shapes, nFaces, shapes1);

	fit_asm.Fitting2(shapes, nFaces, &image, n_iteration);

	shape_to_Mat(shapes, nFaces, *((Mat*)shapes0));

	ENDT("nativeFitting");

	//for(int i = 0; i < shapes[0].NPoints(); i++)
	//	LOGD("points: (%f, %f)", shapes[0][i].x, shapes[0][i].y);

	delete []shapes;
}

JNIEXPORT jboolean JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeVideoFitting
(JNIEnv * jenv, jclass, jlong imageGray, jlong shapes0, jlong frame, jlong n_iteration)
{
	IplImage image = *(Mat*)imageGray;
	Mat shapes1 = *(Mat*)shapes0;	
	bool flag = false;
	if(shapes1.rows == 1)
	{
		asm_shape shape;
	
		BEGINT();

		Mat_to_shape(&shape, 1, shapes1);

		flag = fit_asm.ASMSeqSearch(shape, &image, frame, false, n_iteration);

		shape_to_Mat(&shape, 1, *((Mat*)shapes0));

		ENDT("nativeVideoFitting");
	}

	return flag;
}

static AAM_Shape ShapeAAMFromASM(const asm_shape& shape)
{
	AAM_Shape s;
	s.resize(shape.NPoints());
	for(int i = 0; i < shape.NPoints(); i++)
	{
		s[i].x = shape[i].x;
		s[i].y = shape[i].y;
	}
	return s;
}

JNIEXPORT void JNICALL Java_com_yaoyumeng_asmlibrary_ASMFit_nativeDrawAvatar
(JNIEnv * jenv, jclass, jlong imageColor, jlong shapes0, jboolean zero)
{
	IplImage image = *(Mat*)imageColor;
	Mat shapes1 = *(Mat*)shapes0;	
	if(shapes1.rows == 1)
	{
		asm_shape shape;
	
		BEGINT();

		Mat_to_shape(&shape, 1, shapes1);

		if(avatarImage == NULL)
			aam.Draw(&image, ShapeAAMFromASM(shape),  AAM_Shape(), NULL, zero);
		else
			aam.Draw(&image, ShapeAAMFromASM(shape),  avatarShape, avatarImage, zero);
		
		ENDT("nativeDrawAvatar");
	}
}


#ifdef __cplusplus
}
#endif

