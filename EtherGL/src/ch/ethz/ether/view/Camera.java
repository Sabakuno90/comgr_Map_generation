/*
Copyright (c) 2013, ETH Zurich (Stefan Mueller Arisona, Eva Friedrich)
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, 
  this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
 * Neither the name of ETH Zurich nor the names of its contributors may be 
  used to endorse or promote products derived from this software without
  specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package ch.ethz.ether.view;

import ch.ethz.ether.geom.BoundingVolume;
import ch.ethz.util.MathUtils;

public class Camera {
	private static final boolean KEEP_ROT_X_POSITIVE = true;
	
	private float near = 0.1f;
	// public float far = 1000.0f;
	private float far = Float.POSITIVE_INFINITY;

	private float fov = 45.0f;
	private float distance = 2.0f;
	private float rotateZ = 0.0f;
	private float rotateX = 45.0f;
	private float translateX = 0.0f;
	private float translateY = 0.0f;

	public Camera() {

	}

	public float getNearClippingPlane() {
		return near;
	}

	public float getFarClippingPlane() {
		return far;
	}
	
	public float getFOV() {
		return fov;
	}

	public float getDistance() {
		return distance;
	}

	public void setDistance(float distance) {
		this.distance = distance;
		distance = Math.max(near, distance);
		distance = Math.min(far, distance);
	}

	public void addToDistance(float delta) {
		distance += distance / 10.0 * delta;
		distance = Math.max(near, distance);
		distance = Math.min(far, distance);
	}

	public float getRotateZ() {
		return rotateZ;
	}

	public void setRotateZ(float rotateZ) {
		this.rotateZ = rotateZ;
	}

	public void addToRotateZ(float delta) {
		rotateZ += delta;
	}

	public float getRotateX() {
		return rotateX;
	}

	public void setRotateX(float rotateX) {
		this.rotateX = MathUtils.clamp(rotateX, KEEP_ROT_X_POSITIVE ? 0 : -90, 90);
	}

	public void addToRotateX(float delta) {
		this.rotateX = MathUtils.clamp(rotateX + delta, KEEP_ROT_X_POSITIVE ? 0 : -90, 90);
	}

	public float getTranslateX() {
		return translateX;
	}

	public void setTranslateX(float translateX) {
		this.translateX = translateX;
	}

	public void addToTranslateX(float delta) {
		translateX += distance / 10 * delta;
	}

	public float getTranslateY() {
		return translateY;
	}

	public void setTranslateY(float translateY) {
		this.translateY = translateY;
	}

	public void addToTranslateY(float delta) {
		translateY += distance / 10 * delta;
	}
	
	public void frame(BoundingVolume bounds) {
		float extent = 1.5f * Math.max(Math.max(bounds.getExtentX(), bounds.getExtentY()), bounds.getExtentZ());
		float d = 0.5f * extent / (float)Math.tan(Math.toRadians(fov/2));
		setDistance(d);
		// FIXME hack, assume centered model for now
		setTranslateX(0);
		setTranslateY(0);
	}
}
