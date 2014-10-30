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

package ch.fhnw.util.math;

import java.util.Arrays;

/**
 * 4x4 matrix for dealing with OpenGL 4x4 matrices (column major). In contrast to VecN and Quaternion, Mat4 is mutable.
 *
 * @author radar
 */
public final class Mat4 {
	public static final int M00 = 0;
	public static final int M01 = 4;
	public static final int M02 = 8;
	public static final int M03 = 12;
	public static final int M10 = 1;
	public static final int M11 = 5;
	public static final int M12 = 9;
	public static final int M13 = 13;
	public static final int M20 = 2;
	public static final int M21 = 6;
	public static final int M22 = 10;
	public static final int M23 = 14;
	public static final int M30 = 3;
	public static final int M31 = 7;
	public static final int M32 = 11;
	public static final int M33 = 15;

	public final float[] m;

	public Mat4() {
		m = new float[16];
	}

	public Mat4(float[] m) {
		this.m = m;
	}

	public Mat4(Quaternion q) {
		m = new float[16];
		final float xx = q.x * q.x;
		final float xy = q.x * q.y;
		final float xz = q.x * q.z;
		final float xw = q.x * q.w;
		final float yy = q.y * q.y;
		final float yz = q.y * q.z;
		final float yw = q.y * q.w;
		final float zz = q.z * q.z;
		final float zw = q.z * q.w;

		m[Mat4.M00] = 1 - 2 * (yy + zz);
		m[Mat4.M01] = 2 * (xy - zw);
		m[Mat4.M02] = 2 * (xz + yw);
		m[Mat4.M03] = 0;
		m[Mat4.M10] = 2 * (xy + zw);
		m[Mat4.M11] = 1 - 2 * (xx + zz);
		m[Mat4.M12] = 2 * (yz - xw);
		m[Mat4.M13] = 0;
		m[Mat4.M20] = 2 * (xz - yw);
		m[Mat4.M21] = 2 * (yz + xw);
		m[Mat4.M22] = 1 - 2 * (xx + yy);
		m[Mat4.M23] = 0;
		m[Mat4.M30] = 0;
		m[Mat4.M31] = 0;
		m[Mat4.M32] = 0;
		m[Mat4.M33] = 1;
	}

	/**
	 * Set matrix to zero.
	 */
	public void zero() {
		Arrays.fill(m, 0);
	}

	/**
	 * Set matrix to identity matrix.
	 */
	public void identity() {
		zero();
		m[M00] = m[M11] = m[M22] = m[M33] = 1;
	}

	/**
	 * Assign other matrix to this matrix.
	 *
	 * @param mat
	 *            matrix to be assigned
	 */
	public void assign(Mat4 mat) {
		System.arraycopy(mat.m, 0, m, 0, 16);
	}

	/**
	 * Copy this matrix into new one.
	 *
	 * @return the copy
	 */
	public Mat4 copy() {
		return new Mat4(Arrays.copyOf(m, 16));
	}

	/**
	 * Post-multiply this matrix this = this * mat;
	 *
	 * @param mat
	 *            the second factor of the matrix product
	 */
	public Mat4 postMultiply(final Mat4 mat) {
		for (int i = 0; i < 4; i++) {
			float mi0 = m[i];
			float mi4 = m[i + 4];
			float mi8 = m[i + 8];
			float mi12 = m[i + 12];
			m[i] = mi0 * mat.m[M00] + mi4 * mat.m[M10] + mi8 * mat.m[M20] + mi12 * mat.m[M30];
			m[i + 4] = mi0 * mat.m[M01] + mi4 * mat.m[M11] + mi8 * mat.m[M21] + mi12 * mat.m[M31];
			m[i + 8] = mi0 * mat.m[M02] + mi4 * mat.m[M12] + mi8 * mat.m[M22] + mi12 * mat.m[M32];
			m[i + 12] = mi0 * mat.m[M03] + mi4 * mat.m[M13] + mi8 * mat.m[M23] + mi12 * mat.m[M33];
		}
		return this;
	}

	/**
	 * Pre-multiply this matrix this = mat * this;
	 *
	 * @param mat
	 *            the first factor of the matrix product
	 */
	public Mat4 preMultiply(final Mat4 mat) {
		for (int i = 0; i < 16; i += 4) {
			float mi0 = m[i];
			float mi1 = m[i + 1];
			float mi2 = m[i + 2];
			float mi3 = m[i + 3];
			m[i] = mi0 * mat.m[M00] + mi1 * mat.m[M01] + mi2 * mat.m[M02] + mi3 * mat.m[M03];
			m[i + 1] = mi0 * mat.m[M10] + mi1 * mat.m[M11] + mi2 * mat.m[M12] + mi3 * mat.m[M13];
			m[i + 2] = mi0 * mat.m[M20] + mi1 * mat.m[M21] + mi2 * mat.m[M22] + mi3 * mat.m[M23];
			m[i + 3] = mi0 * mat.m[M30] + mi1 * mat.m[M31] + mi2 * mat.m[M32] + mi3 * mat.m[M33];
		}
		return this;
	}

	/**
	 * Pre-multiplies matrix m with translation matrix t (m = t * m)
	 *
	 * @param tx
	 *            x translation
	 * @param ty
	 *            y translation
	 * @param tz
	 *            z translation
	 */
	public Mat4 translate(float tx, float ty, float tz) {
		final Mat4 t = identityMatrix();
		t.m[M03] = tx;
		t.m[M13] = ty;
		t.m[M23] = tz;
		return preMultiply(t);
	}

	public Mat4 translate(Vec3 t) {
		return translate(t.x, t.y, t.z);
	}

	/**
	 * Pre-multiplies matrix m with rotation matrix r (m = r * m).
	 *
	 * @param angle
	 *            rotation angle in degrees
	 * @param x
	 *            rotation axis x
	 * @param y
	 *            rotation axis y
	 * @param z
	 *            rotation axis z
	 */
	public Mat4 rotate(float angle, float x, float y, float z) {
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		if (l != 0 && l != 1) {
			l = 1.0f / l;
			x *= l;
			y *= l;
			z *= l;
		}

		float radians = angle * MathUtil.DEGREES_TO_RADIANS;
		float c = (float) Math.cos(radians);
		float ic = 1.0f - c;
		float s = (float) Math.sin(radians);

		float xy = x * y;
		float xz = x * z;
		float xs = x * s;
		float ys = y * s;
		float yz = y * z;
		float zs = z * s;

		final Mat4 r = identityMatrix();
		r.m[M00] = x * x * ic + c;
		r.m[M10] = xy * ic + zs;
		r.m[M20] = xz * ic - ys;
		r.m[M01] = xy * ic - zs;
		r.m[M11] = y * y * ic + c;
		r.m[M21] = yz * ic + xs;
		r.m[M02] = xz * ic + ys;
		r.m[M12] = yz * ic - xs;
		r.m[M22] = z * z * ic + c;

		return preMultiply(r);
	}

	public Mat4 rotate(float angle, Vec3 axis) {
		return rotate(angle, axis.x, axis.y, axis.z);
	}

	/**
	 * Multiplies matrix m with scale matrix s (m = s * m = m * s).
	 *
	 * @param sx
	 *            scale x factor
	 * @param sy
	 *            scale y factor
	 * @param sz
	 *            scale z factor
	 */
	public Mat4 scale(float sx, float sy, float sz) {
		m[M00] *= sx;
		m[M11] *= sy;
		m[M22] *= sz;
		m[M03] *= sx;
		m[M13] *= sy;
		m[M23] *= sz;
		return this;
	}

	public Mat4 scale(Vec3 s) {
		return scale(s.x, s.y, s.z);
	}

	/**
	 * Transform vector result = m * vec.
	 *
	 * @param vec
	 *            the vector to be transformed
	 * @return the transformed vector
	 */
	public Vec4 transform(Vec4 vec) {
		float x = vec.x * m[M00] + vec.y * m[M01] + vec.z * m[M02] + vec.w * m[M03];
		float y = vec.x * m[M10] + vec.y * m[M11] + vec.z * m[M12] + vec.w * m[M13];
		float z = vec.x * m[M20] + vec.y * m[M21] + vec.z * m[M22] + vec.w * m[M23];
		float w = vec.x * m[M30] + vec.y * m[M31] + vec.z * m[M32] + vec.w * m[M33];
		return new Vec4(x, y, z, w);
	}

	/**
	 * Transform vector result = m * vec (divided by w).
	 *
	 * @param vec
	 *            the vector to be transformed
	 * @return the transformed vector
	 */
	public Vec3 transform(Vec3 vec) {
		float x = vec.x * m[M00] + vec.y * m[M01] + vec.z * m[M02] + m[M03];
		float y = vec.x * m[M10] + vec.y * m[M11] + vec.z * m[M12] + m[M13];
		float z = vec.x * m[M20] + vec.y * m[M21] + vec.z * m[M22] + m[M23];
		float w = vec.x * m[M30] + vec.y * m[M31] + vec.z * m[M32] + m[M33];
		return new Vec3(x / w, y / w, z / w);
	}

	/**
	 * Transform a float array of xyz vectors.
	 *
	 * @param xyz
	 *            the input array of vectors to be transformed
	 * @param result
	 *            the array where to store the transformed vectors or NULL to create a new array
	 * @return the transformed result
	 */
	public float[] transform(float[] xyz, float[] result) {
		if (xyz == null)
			return null;
		if (result == null)
			result = new float[xyz.length];
		for (int i = 0; i < xyz.length; i += 3) {
			float x = xyz[i] * m[M00] + xyz[i + 1] * m[M01] + xyz[i + 2] * m[M02] + m[M03];
			float y = xyz[i] * m[M10] + xyz[i + 1] * m[M11] + xyz[i + 2] * m[M12] + m[M13];
			float z = xyz[i] * m[M20] + xyz[i + 1] * m[M21] + xyz[i + 2] * m[M22] + m[M23];
			float w = xyz[i] * m[M30] + xyz[i + 1] * m[M31] + xyz[i + 2] * m[M32] + m[M33];
			result[i] = x / w;
			result[i + 1] = y / w;
			result[i + 2] = z / w;
		}
		return result;
	}

	/**
	 * Transform a float array of xyz vectors.
	 *
	 * @param xyz
	 *            the input array of vectors to be transformed
	 * @return new array containing the transformed result
	 */
	public float[] transform(float[] xyz) {
		return transform(xyz, null);
	}

	/**
	 * Get transposed matrix.
	 *
	 * @return the transposed matrix
	 */
	public Mat4 transposed() {
		Mat4 result = new Mat4();
		result.m[M00] = m[M00];
		result.m[M10] = m[M01];
		result.m[M20] = m[M02];
		result.m[M30] = m[M03];
		result.m[M01] = m[M10];
		result.m[M11] = m[M11];
		result.m[M21] = m[M12];
		result.m[M31] = m[M13];
		result.m[M02] = m[M20];
		result.m[M12] = m[M21];
		result.m[M22] = m[M22];
		result.m[M32] = m[M33];
		result.m[M03] = m[M30];
		result.m[M13] = m[M31];
		result.m[M23] = m[M32];
		result.m[M33] = m[M33];
		return result;
	}

	/**
	 * Get inverse matrix.
	 *
	 * @return the inverse or null if a is singular
	 */
	public Mat4 inverse() {
		final float[][] temp = new float[4][4];

		for (int i = 0; i < 4; i++) {
			System.arraycopy(m, i * 4, temp[i], 0, 4);
		}

		Mat4 result = identityMatrix();

		for (int i = 0; i < 4; i++) {
			// look for largest element in column
			int swap = i;
			for (int j = i + 1; j < 4; j++) {
				if (Math.abs(temp[j][i]) > Math.abs(temp[i][i])) {
					swap = j;
				}
			}

			if (swap != i) {
				// swap rows
				for (int k = 0; k < 4; k++) {
					float t = temp[i][k];
					temp[i][k] = temp[swap][k];
					temp[swap][k] = t;

					t = result.m[i * 4 + k];
					result.m[i * 4 + k] = result.m[swap * 4 + k];
					result.m[swap * 4 + k] = t;
				}
			}

			if (temp[i][i] == 0) {
				// singular input
				return null;
			}

			float t = temp[i][i];
			for (int k = 0; k < 4; k++) {
				temp[i][k] /= t;
				result.m[i * 4 + k] /= t;
			}
			for (int j = 0; j < 4; j++) {
				if (j != i) {
					t = temp[j][i];
					for (int k = 0; k < 4; k++) {
						temp[j][k] -= temp[i][k] * t;
						result.m[j * 4 + k] -= result.m[i * 4 + k] * t;
					}
				}
			}
		}
		return result;
	}

	/**
	 * Create new identity matrix.
	 *
	 * @return the new identity matrix
	 */
	public static Mat4 identityMatrix() {
		Mat4 result = new Mat4();
		result.m[M00] = result.m[M11] = result.m[M22] = result.m[M33] = 1;
		return result;
	}

	/**
	 * Multiplies two matrices result = a * b.
	 *
	 * @param a
	 *            4x4 matrix in column-major order
	 * @param b
	 *            4x4 matrix in column-major order
	 * @return multiplied column-major matrix
	 */
	public static Mat4 product(Mat4 a, Mat4 b) {
		Mat4 result = new Mat4();
		for (int i = 0; i < 4; i++) {
			float ai0 = a.m[i];
			float ai1 = a.m[i + 4];
			float ai2 = a.m[i + 8];
			float ai3 = a.m[i + 12];
			result.m[i] = ai0 * b.m[M00] + ai1 * b.m[M10] + ai2 * b.m[M20] + ai3 * b.m[M30];
			result.m[i + 4] = ai0 * b.m[M01] + ai1 * b.m[M11] + ai2 * b.m[M21] + ai3 * b.m[M31];
			result.m[i + 8] = ai0 * b.m[M02] + ai1 * b.m[M12] + ai2 * b.m[M22] + ai3 * b.m[M32];
			result.m[i + 12] = ai0 * b.m[M03] + ai1 * b.m[M13] + ai2 * b.m[M23] + ai3 * b.m[M33];
		}
		return result;
	}

	/**
	 * Create view matrix from position (eye point), target (center/reference point) and up vector.
	 * 
	 * @param position
	 *            camera position in world coordinates
	 * @param target
	 *            camera target in world coordinates
	 * @param up
	 *            camera up vector in world coordinates
	 * @return view matrix
	 */
	public static Mat4 lookAt(Vec3 position, Vec3 target, Vec3 up) {
		up = up.normalize();
		Vec3 f = target.subtract(position).normalize();
		Vec3 s = f.cross(up);
		Vec3 u = s.normalize().cross(f);
		Vec3 t = position.negate();

		Mat4 result = new Mat4();
		result.m[M00] = s.x;
		result.m[M10] = u.x;
		result.m[M20] = -f.x;
		result.m[M01] = s.y;
		result.m[M11] = u.y;
		result.m[M21] = -f.y;
		result.m[M02] = s.z;
		result.m[M12] = u.z;
		result.m[M22] = -f.z;
		result.m[M03] = s.x * t.x + s.y * t.y + s.z * t.z;
		result.m[M13] = u.x * t.x + u.y * t.y + u.z * t.z;
		result.m[M23] = -f.x * t.x - f.y * t.y - f.z * t.z;
		result.m[M33] = 1;
		return result;
	}

	/**
	 * Create perspective projection matrix. Supports far plane at infinity.
	 *
	 * @param fov
	 *            field of view (degrees)
	 * @param aspect
	 *            aspect ratio
	 * @param near
	 *            near plane
	 * @param far
	 *            far plane (set to Float.POSITIVE_INFINITY for far plane at infinity)
	 * @return perspective projection matrix
	 */
	public static Mat4 perspective(float fov, float aspect, float near, float far) {
		double radians = fov / 2 * MathUtil.DEGREES_TO_RADIANS;
		double sine = Math.sin(radians);
		float deltaZ = far - near;

		if ((deltaZ == 0) || (sine == 0) || (aspect == 0)) {
			throw new IllegalArgumentException("illegal arguments (fovy=" + fov + " aspect=" + aspect + " near=" + near + " far=" + far);
		}

		double cotangent = (float) (Math.cos(radians) / sine);

		Mat4 result = new Mat4();
		result.m[M00] = (float) (cotangent / aspect);
		result.m[M11] = (float) cotangent;
		result.m[M22] = ((far >= Double.POSITIVE_INFINITY) ? -1 : (-(far + near) / deltaZ));
		result.m[M32] = -1;
		result.m[M23] = ((far >= Double.POSITIVE_INFINITY) ? (-2 * near) : (-2 * near * far / deltaZ));
		return result;
	}

	/**
	 * Create an orthographic projection matrix.
	 *
	 * @param left
	 *            coordinate for left vertical clipping plane
	 * @param right
	 *            coordinate for right vertical clipping plane
	 * @param top
	 *            coordinate for top horizontal clipping plane
	 * @param bottom
	 *            coordinate for bottom horizontal clipping plane
	 * @param near
	 *            near plane
	 * @param far
	 *            far plane
	 * @return orthographic projection matrix
	 */
	public static Mat4 ortho(float left, float right, float bottom, float top, float near, float far) {
		float dx = right - left;
		float dy = top - bottom;
		float dz = far - near;
		float tx = -1.0f * (right + left) / dx;
		float ty = -1.0f * (top + bottom) / dy;
		float tz = -1.0f * (far + near) / dz;

		Mat4 result = new Mat4();
		result.m[M00] = 2.0f / dx;
		result.m[M11] = 2.0f / dy;
		result.m[M22] = -2.0f / dz;
		result.m[M03] = tx;
		result.m[M13] = ty;
		result.m[M23] = tz;
		result.m[M33] = 1;
		return result;
	}

	@Override
	public String toString() {
		//@formatter:off
		return String.format("[% .2f,% .2f,% .2f,% .2f\n % .2f,% .2f,% .2f,% .2f\n % .2f,% .2f,% .2f,% .2f\n % .2f,% .2f,% .2f,% .2f ]\n\n",
							 m[M00], m[M01], m[M02], m[M03],
							 m[M10], m[M11], m[M12], m[M13],
							 m[M20], m[M21], m[M22], m[M23],
							 m[M30], m[M31], m[M32], m[M33]);
		//@formatter:on
	}
}