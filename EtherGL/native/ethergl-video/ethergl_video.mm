//
//  ethergl_video.m
//  ethergl-video
//
//  Created by Stefan Müller Arisona on 29/06/14.
//  Copyright (c) 2014 Corebounce Association. All rights reserved.
//

#import "JavaNativeFoundation/JNFJNI.h"

#include <string>
#include <OpenGL/gl3.h>

#import <AVFoundation/AVFoundation.h>


#import "ethergl_video.h"

#define MSG(...) { printf(__VA_ARGS__); fflush(stdout); }

class AVAssetWrapper {
private:
    std::string url;
    AVAssetReader* reader;
    AVAssetImageGenerator* generator;
    
    double duration;
    double frameRate;
    CGSize size;
    
public:
    AVAssetWrapper(std::string url) : url(url), reader(nullptr), generator(nullptr) {
        rewind();
        //MSG("avfoundation asset: %s: duration=%f framerate=%f size=%dx%d\n", url.c_str(), duration, frameRate, (int)size.width, (int)size.height);
    }
    
    ~AVAssetWrapper() {
        [reader release];
        [generator release];
    }
    
    double getDuration() {
        return duration;
    }
    
    double getFrameRate() {
        return frameRate;
    }
    
    long getFrameCount() {
        return duration * frameRate;
    }
    
    int getWidth() {
        return size.width;
    }
    
    int getHeight() {
        return size.height;
    }

    void rewind() {
        if (reader != nullptr) {
            [reader release];
            [generator release];
        }
        
		NSURL* nsUrl = [NSURL URLWithString:[NSString stringWithCString:url.c_str() encoding:NSUTF8StringEncoding]];
		if (!nsUrl) {
			MSG("avassetwrapper: invalid url '%s'\n", url.c_str());
            throw std::invalid_argument("invalid url");
		}
		
        NSDictionary* options = @{ AVURLAssetPreferPreciseDurationAndTimingKey : @YES };
        
		AVAsset* asset = [AVURLAsset URLAssetWithURL:nsUrl options:options];
		if (!asset) {
			MSG("avassetwrapper: invalid url '%s'\n", url.c_str());
            throw std::invalid_argument("invalid url");
		}
        
		NSArray* tracks = [asset tracksWithMediaType:AVMediaTypeVideo];
		if ([tracks count] < 1) {
			MSG("avassetwrapper: no video track for '%s'\n", url.c_str());
            throw std::invalid_argument("no video track");
		}
		AVAssetTrack* videoTrack = [tracks objectAtIndex:0];
        
        duration = CMTimeGetSeconds([asset duration]);
		frameRate = [videoTrack nominalFrameRate];
        size = [videoTrack naturalSize];
		
        // create reader (for sequential frame-by-frame access)
        
		NSError* error = nil;
		reader = [[AVAssetReader alloc] initWithAsset:asset error:&error];
		if (!reader || error) {
			MSG("avassetwrapper: could not initialize reader for '%s'\n", url.c_str());
            throw std::invalid_argument("could not initialize reader");
		}
		
		NSDictionary* settings = [NSDictionary dictionaryWithObjectsAndKeys:
								  [NSNumber numberWithUnsignedInt:kCVPixelFormatType_32BGRA],
								  (NSString*)kCVPixelBufferPixelFormatTypeKey,
								  nil];
		[reader addOutput:[AVAssetReaderTrackOutput assetReaderTrackOutputWithTrack:videoTrack outputSettings:settings]];
		if ([reader startReading] != YES) {
			[reader release];
			MSG("avfoundation: could not start reading from '%s': %s\n", url.c_str(), [[[reader error] localizedDescription] UTF8String]);
            throw std::invalid_argument("could not start reading");
		}
        
        // create generator (for random access)
        generator = [[AVAssetImageGenerator alloc] initWithAsset:asset];
    }
    
    jbyteArray getFrame(JNIEnv* env, double time) {
        CMTime cmtime = CMTimeMakeWithSeconds(time, 600);
        
        CMTime actualTime;
        NSError *error;
        CGImageRef image = [generator copyCGImageAtTime:cmtime actualTime:&actualTime error:&error];
        
        if (!image)
            return nullptr;

        NSData* data = (NSData*)CGDataProviderCopyData(CGImageGetDataProvider(image));

        // same here (as below): we have to make sure, we return an image that has the size of getWidth/getHeight
        //int width = (int)CGImageGetWidth(image);
        //int height = (int)CGImageGetHeight(image);
        int width = getWidth();
        int height = getHeight();
        int bytesPerRow = (int)CGImageGetBytesPerRow(image);
        int skip = bytesPerRow - width * 4;
        int length = width * height * 4;
        //MSG("w=%d h=%d bpr=%d skip=%d length=%d\n", width, height, bytesPerRow, skip, length);
        
        jbyteArray array = env->NewByteArray(length);
        uint8_t* arrayElements = (uint8_t*)env->GetByteArrayElements(array, nullptr);
        uint8_t* dst = arrayElements;
        for (int y = height; --y >= 0;) {
            uint8_t* src = (uint8_t*)[data bytes] + y * bytesPerRow;
            for (int x = 0; x < width; ++x) {
                uint8_t a = *src++;
                uint8_t r = *src++;
                uint8_t g = *src++;
                uint8_t b = *src++;
                *dst++ = r;
                *dst++ = g;
                *dst++ = b;
                *dst++ = a;
            }
            src += skip;
        }
        env->ReleaseByteArrayElements(array, (jbyte*)arrayElements, 0);

        [data release];
        CGImageRelease(image);
        
        return array;
    }

    jbyteArray getNextFrame(JNIEnv* env) {
        if ([reader status] != AVAssetReaderStatusReading) {
            MSG("get next frame: reached end of movie\n");
            return nullptr;
        }
        
        AVAssetReaderOutput* output = [reader.outputs objectAtIndex:0];
        CMSampleBufferRef sampleBuffer = [output copyNextSampleBuffer];
        if (!sampleBuffer) {
            MSG("get next frame: could not copy sample buffer\n");
            return nullptr;
        }
        
        /*
        AudioBufferList  audioBufferList;
        CMBlockBufferRef blockBuffer;
        NSMutableData *data=[[NSMutableData alloc] init];
        CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(sampleBuffer, NULL, &audioBufferList, sizeof(audioBufferList), NULL, NULL, 0, &blockBuffer);
        */
        
        CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
        
        // lock the image buffer
        CVPixelBufferLockBaseAddress(imageBuffer, 0);
        
        // XXX: note if movie width cannot be divided by 4 it seems the movie is scaled up to the next width that can
        // i.e. if you open a moive with 1278 pixels width, here, the imageBuffer will have a width of 1280. this of course
        // screws up our interface further up a bit, which relies on movie.getWidth ... thus we for now just ignore the scaling.
        //int width = (int)CVPixelBufferGetWidth(imageBuffer);
        //int height = (int)CVPixelBufferGetHeight(imageBuffer);
        int width = getWidth();
        int height = getHeight();
        int bytesPerRow = (int)CVPixelBufferGetBytesPerRow(imageBuffer);
        int skip = bytesPerRow - width * 4;
        int length = width * height * 4;
        //MSG("w=%d h=%d bpr=%d skip=%d length=%d\n", width, height, bytesPerRow, skip, length);

        jbyteArray array = env->NewByteArray((int)length);
        uint8_t* arrayElements = (uint8_t*)env->GetByteArrayElements(array, nullptr);
        uint8_t* dst = arrayElements;
        for (int y = height; --y >= 0;) {
            uint8_t* src = (uint8_t*)CVPixelBufferGetBaseAddress(imageBuffer) + y * bytesPerRow;
            for (int x = 0; x < width; ++x) {
                uint8_t b = *src++;
                uint8_t g = *src++;
                uint8_t r = *src++;
                uint8_t a = *src++;
                *dst++ = r;
                *dst++ = g;
                *dst++ = b;
                *dst++ = a;
            }
            src += skip;
        }
        env->ReleaseByteArrayElements(array, (jbyte*)arrayElements, 0);
        
        /*
        // process audio samples
        for( int y=0; y<audioBufferList.mNumberBuffers; y++ ) {
            AudioBuffer audioBuffer = audioBufferList.mBuffers[y];
            Float32 *frame = (Float32*)audioBuffer.mData;
            [data appendBytes:frame length:audioBuffer.mDataByteSize];
        }
        */
        
        // unlock the image buffer & cleanup
        CVPixelBufferUnlockBaseAddress(imageBuffer, 0);
        CFRelease(sampleBuffer);
        
        /*
        // release audio buffer
        CFRelease(blockBuffer);
        [data release];
        */
        
        return array;
    }
    
    int getNextTexture(uint64_t* data) {
        // Actually, this was intended for the core video
        // OpenGl texture cache. But because the Apple
        // provides texture coordinates instead of flipping
        // the data coorectly it's pretty useless for
        // EtherGL and thus we emulate it.
        
        if ([reader status] != AVAssetReaderStatusReading) {
            MSG("get next texture: reached end of movie\n");
            return -1;
        }
        
        AVAssetReaderOutput* output = [reader.outputs objectAtIndex:0];
        CMSampleBufferRef sampleBuffer = [output copyNextSampleBuffer];
        if (!sampleBuffer) {
            MSG("get next texture: could not copy sample buffer\n");
            return -2;
        }
        
        CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
        
        // lock the image buffer
        CVPixelBufferLockBaseAddress(imageBuffer, 0);
        
        // XXX: note if movie width cannot be divided by 4 it seems the movie is scaled up to the next width that can
        // i.e. if you open a moive with 1278 pixels width, here, the imageBuffer will have a width of 1280. this of course
        // screws up our interface further up a bit, which relies on movie.getWidth ... thus we for now just ignore the scaling.
        //int width = (int)CVPixelBufferGetWidth(imageBuffer);
        //int height = (int)CVPixelBufferGetHeight(imageBuffer);
        int width = getWidth();
        int height = getHeight();
        int bytesPerRow = (int)CVPixelBufferGetBytesPerRow(imageBuffer);
        
        GLuint texture;
        glGenTextures(1, &texture);
        glBindTexture(GL_TEXTURE_2D, texture);
        
        uint8_t* src = (uint8_t*)CVPixelBufferGetBaseAddress(imageBuffer);
        
        uint8_t* tmp = new uint8_t[bytesPerRow];
        
        for(int i = height / 2, y0 = 0, y1 = height - 1; --i >= 0; y0++, y1--) {
            memcpy(tmp, src + bytesPerRow * y0, bytesPerRow);
            memcpy(src + bytesPerRow * y0, src + bytesPerRow * y1, bytesPerRow);
            memcpy(src + bytesPerRow * y1, tmp, bytesPerRow);
        }
        
        delete[] tmp;
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, src);
        glGenerateMipmap(GL_TEXTURE_2D);
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glFinish();
        
        data[ch_fhnw_ether_video_avfoundation_AVAsset_IDX_TARGET]  = GL_TEXTURE_2D;
        data[ch_fhnw_ether_video_avfoundation_AVAsset_IDX_NAME]    = texture;
        data[ch_fhnw_ether_video_avfoundation_AVAsset_IDX_WIDTH]   = width;
        data[ch_fhnw_ether_video_avfoundation_AVAsset_IDX_HEIGHT]  = height;

        // unlock the image buffer & cleanup
        CVPixelBufferUnlockBaseAddress(imageBuffer, 0);
        CFRelease(sampleBuffer);
        
        return 0;
    }
    
    int disposeTexture(uint64_t* data) {
        GLuint texture = (GLuint)data[ch_fhnw_ether_video_avfoundation_AVAsset_IDX_NAME];
        glDeleteTextures(1, &texture);
        return 0;
    }
    
    int getNumChannels() {
        return 2;
    }
    
    float getSampleRate() {
        return 48000;
    }
};


/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeCreate
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeCreate
(JNIEnv * env, jclass, jstring javaURL) {
    JNF_COCOA_ENTER(env);
    
    const char* url = env->GetStringUTFChars(javaURL, JNI_FALSE);
    
    jlong nativeHandle = 0;
    try {
        nativeHandle = (jlong)new AVAssetWrapper(url);
    } catch(std::exception& e) {
        // fall through, return zero
    }
    
    env->ReleaseStringUTFChars(javaURL, url);
    
    return nativeHandle;

    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeDispose
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeDispose
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);
    
    delete (AVAssetWrapper*)nativeHandle;

    JNF_COCOA_EXIT(env);
}


/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetDuration
 * Signature: (J)D
 */
JNIEXPORT jdouble JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetDuration
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);
    
    return ((AVAssetWrapper*)nativeHandle)->getDuration();
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetFrameRate
 * Signature: (J)D
 */
JNIEXPORT jdouble JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetFrameRate
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);
    
    return ((AVAssetWrapper*)nativeHandle)->getFrameRate();
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetFrameCount
 * Signature: (J)I
 */
JNIEXPORT jlong JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetFrameCount
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);
    
    return ((AVAssetWrapper*)nativeHandle)->getFrameCount();
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetWidth
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetWidth
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);
    
    return ((AVAssetWrapper*)nativeHandle)->getWidth();
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetHeight
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetHeight
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);
    
    return ((AVAssetWrapper*)nativeHandle)->getHeight();
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeRewind
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeRewind
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);

    ((AVAssetWrapper*)nativeHandle)->rewind();
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetFrame
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetFrame
(JNIEnv * env, jclass, jlong nativeHandle, jdouble time) {
    JNF_COCOA_ENTER(env);

    return ((AVAssetWrapper*)nativeHandle)->getFrame(env, time);
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetNextFrame
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetNextFrame
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);

    return ((AVAssetWrapper*)nativeHandle)->getNextFrame(env);
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetNextTextureAndLock
 * Signature: (J[J)I
 */
JNIEXPORT jint JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetNextTexture
(JNIEnv* env, jclass, jlong nativeHandle, jlongArray data) {
    JNF_COCOA_ENTER(env);
    
    uint64_t* arrayElements = (uint64_t*)env->GetLongArrayElements(data, nullptr);
    
    int result = ((AVAssetWrapper*)nativeHandle)->getNextTexture(arrayElements);
    
    env->SetLongArrayRegion(data, 0, env->GetArrayLength(data), (jlong*)arrayElements);

    return result;
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeUnlockTexture
 * Signature: (J[J)I
 */
JNIEXPORT jint JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeDisposeTexture
(JNIEnv* env, jclass, jlong nativeHandle, jlongArray data) {
    JNF_COCOA_ENTER(env);
    
    uint64_t* arrayElements = (uint64_t*)env->GetLongArrayElements(data, nullptr);
    
    return ((AVAssetWrapper*)nativeHandle)->disposeTexture(arrayElements);
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetNumChannels
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetNumChannels
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);
    
    return ((AVAssetWrapper*)nativeHandle)->getNumChannels();
    
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     ch_fhnw_ether_video_avfoundation_AVAsset
 * Method:    nativeGetSampleRate
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL Java_ch_fhnw_ether_video_avfoundation_AVAsset_nativeGetSampleRate
(JNIEnv * env, jclass, jlong nativeHandle) {
    JNF_COCOA_ENTER(env);
    
    return ((AVAssetWrapper*)nativeHandle)->getSampleRate();
    
    JNF_COCOA_EXIT(env);
}


