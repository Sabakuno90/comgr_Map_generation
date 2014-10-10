/*
 * Copyright (c) 2013 - 2014 Stefan Muller Arisona, Simon Schubiger, Samuel von Stachelski
 * Copyright (c) 2013 - 2014 FHNW & ETH Zurich
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *  Neither the name of FHNW / ETH Zurich nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package ch.fhnw.ether.camera;

import ch.fhnw.util.math.Mat4;
import ch.fhnw.util.math.Vec3;
import ch.fhnw.util.math.geometry.BoundingBox;

public class Camera implements ICamera {
	private static final boolean KEEP_ROT_X_POSITIVE = true;
	private static final float MIN_ZOOM = 0.02f;

	private float fov;
	private float aspect;
	private float near;
	private float far;

	private Mat4 projectionMatrix = Mat4.identityMatrix();
	private Mat4 cameraMatrix = Mat4.identityMatrix();

	private float orbitRadius = 3;
	private float azimut = 0;
	private float elevation = 0;

	public Camera() {
		this(45, 1, 0.01f, 100000);
	}

	public Camera(float fov, float aspect, float near, float far) {
		this.fov = fov;
		this.aspect = aspect;
		this.near = near;
		this.far = far;
		move(0, -orbitRadius, 0, true);
	}

	@Override
	public float getFov() {
		return fov;
	}

	@Override
	public void setFov(float fov) {
		this.fov = fov;
	}

	@Override
	public float getAspect() {
		return aspect;
	}

	@Override
	public void setAspect(float aspect) {
		this.aspect = aspect;
	}

	@Override
	public float getNear() {
		return near;
	}

	@Override
	public void setNear(float near) {
		this.near = near;
	}

	@Override
	public float getFar() {
		return far;
	}

	@Override
	public void setFar(float far) {
		this.far = far;
	}

	@Override
	public Mat4 getProjectionMatrix() {
		projectionMatrix.perspective(fov, aspect, near, far);
		return projectionMatrix;
	}

	@Override
	public void setProjectionMatrix(Mat4 projectionMatrix) {
		this.projectionMatrix = projectionMatrix;
		// these values are now dirty
		fov = aspect = near = far = -1;
	}

	@Override
	public Mat4 getViewMatrix() {
		Mat4 viewMatrix = cameraMatrix.inverse();
		// Align to coordinate space with Z=up ad Y=depth
		viewMatrix.rotate(-90, Vec3.X);
		return viewMatrix;
	}

	@Override
	public void setViewMatrix(Mat4 viewMatrix) {
		cameraMatrix = viewMatrix.inverse();
	}

	@Override
	public Mat4 getViewProjMatrix() {
		return Mat4.product(getProjectionMatrix(), getViewMatrix());
	}

	@Override
	public Mat4 getViewProjInvMatrix() {
		return getViewProjMatrix().inverse();
	}

	@Override
	public void move(float x, float y, float z, boolean localTransformation) {
		Mat4 move = Mat4.identityMatrix();
		move.translate(x, y, z);
		if (localTransformation) {
			cameraMatrix = Mat4.product(cameraMatrix, move);
		} else {
			cameraMatrix = Mat4.product(move, cameraMatrix);
		}
	}

	@Override
	public void turn(float amount, Vec3 axis, boolean localTransformation) {
		Mat4 turn = Mat4.identityMatrix();
		turn.rotate(amount, axis);
		if (localTransformation) {
			cameraMatrix = Mat4.product(cameraMatrix, turn);
		} else {
			cameraMatrix = Mat4.product(turn, cameraMatrix);
		}
	}

	@Override
	public void setRotation(float xAxis, float yAxis, float zAxis) {
		float x = cameraMatrix.m[Mat4.M03];
		float y = cameraMatrix.m[Mat4.M13];
		float z = cameraMatrix.m[Mat4.M23];
		cameraMatrix = Mat4.identityMatrix();
		cameraMatrix.rotate(xAxis, Vec3.X);
		cameraMatrix.rotate(yAxis, Vec3.Y);
		cameraMatrix.rotate(zAxis, Vec3.Z);
		cameraMatrix.translate(x, y, z);
	}

	@Override
	public void setPosition(Vec3 position) {
		setPosition(position.x, position.y, position.z);
	}

	@Override
	public void setPosition(float x, float y, float z) {
		cameraMatrix.m[Mat4.M03] = x;
		cameraMatrix.m[Mat4.M13] = y;
		cameraMatrix.m[Mat4.M23] = z;
	}

	@Override
	public Vec3 getPosition() {
		return new Vec3(cameraMatrix.m[Mat4.M03],
				cameraMatrix.m[Mat4.M13], cameraMatrix.m[Mat4.M23]);
	}

	@Override
	public Vec3 getLookDirection() {
		return new Vec3(cameraMatrix.m[Mat4.M01], cameraMatrix.m[Mat4.M11],
				cameraMatrix.m[Mat4.M21]);
	}

	@Override
	public BoundingBox getBoundings() {
		BoundingBox b = new BoundingBox();
		b.add(getPosition());
		return b;
	}

	// orbit camera
	// methods--------------------------------------------------------------
	// a call of one of this methods will change the camera mode to orbit mode

	@Override
	public void ORBITzoom(float zoomFactor) {
		float old_radius = orbitRadius;
		orbitRadius *= zoomFactor;
		if (orbitRadius < MIN_ZOOM) {
			orbitRadius = old_radius;
			return;
		}
		move(0, old_radius - orbitRadius, 0, true);
	}

	@Override
	public void ORBITturnAzimut(float amount) {
		// move camera pivot to world center
		Vec3 pivot_position = ORBITgetPivotPosition();
		cameraMatrix.translate(pivot_position.negate());

		// move camera to pivot center
		move(0, orbitRadius, 0, true);

		// rotate camera round global Z-axis
		turn(amount, Vec3.Z, false);
		
		// move camera back to orbit position
		move(0, -orbitRadius, 0, true);

		// move pivot back to origin position
		cameraMatrix.translate(pivot_position);
		azimut += amount;
	}

	@Override
	public void ORBITturnElevation(float amount) {
		if (KEEP_ROT_X_POSITIVE) {
			if (elevation - amount < 0)
				amount = elevation;
			if (elevation - amount > 90)
				amount = 90 - elevation;
		}
		move(0, orbitRadius, 0, true);
		turn(amount, Vec3.X, true);
		move(0, -orbitRadius, 0, true);
		this.elevation -= amount;
	}

	@Override
	public void ORBITsetZoom(float zoom) {
		if (zoom < MIN_ZOOM)
			zoom = MIN_ZOOM;
		move(0, orbitRadius - zoom, 0, true);
		orbitRadius = zoom;
	}

	@Override
	public void ORBITsetAzimut(float azimut) {
		float diff = azimut - this.azimut;
		Vec3 pivot_position = ORBITgetPivotPosition();
		cameraMatrix.translate(pivot_position.negate());
		move(0, orbitRadius, 0, true);
		turn(diff, Vec3.Z, false);
		move(0, -orbitRadius, 0, true);
		cameraMatrix.translate(pivot_position);
		this.azimut = azimut;
	}

	@Override
	public void ORBITsetElevation(float elevation) {
		if (KEEP_ROT_X_POSITIVE) {
			if (elevation < 0)
				elevation = 0;
			if (elevation > 90)
				elevation = 90;
		}
		float diff = this.elevation - elevation;
		move(0, orbitRadius, 0, true);
		turn(diff, Vec3.X, true);
		move(0, -orbitRadius, 0, true);
		this.elevation = elevation;
	}

	@Override
	public void ORBITmovePivot(float x, float y, float z,
			boolean localTransformation) {
		float newX = x, newY = y;
		if (localTransformation) {
			float azimut_rad = (float) Math.toRadians(-azimut);
			newX = (float) (Math.cos(azimut_rad) * x + Math.sin(azimut_rad) * y);
			newY = (float) (-Math.sin(azimut_rad) * x + Math.cos(azimut_rad)
					* y);
		}
		cameraMatrix.translate(newX, newY, z);
	}

	@Override
	public Vec3 ORBITgetPivotPosition() {
		Vec3 cameraPosition = getPosition();
		Vec3 pivot_position = cameraPosition.add(getLookDirection().scale(
				orbitRadius));
		return pivot_position;
	}

	@Override
	public float ORBIgetZoom() {
		return orbitRadius;
	}

}
