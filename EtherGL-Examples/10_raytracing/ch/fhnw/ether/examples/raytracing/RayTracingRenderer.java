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

package ch.fhnw.ether.examples.raytracing;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import ch.fhnw.ether.camera.ICamera;
import ch.fhnw.ether.examples.raytracing.util.IntersectResult;
import ch.fhnw.ether.examples.raytracing.util.Ray;
import ch.fhnw.ether.render.IRenderer;
import ch.fhnw.ether.render.forward.ForwardRenderer;
import ch.fhnw.ether.render.gl.Texture;
import ch.fhnw.ether.scene.light.ILight;
import ch.fhnw.ether.scene.mesh.DefaultMesh;
import ch.fhnw.ether.scene.mesh.IAttribute;
import ch.fhnw.ether.scene.mesh.IMesh;
import ch.fhnw.ether.scene.mesh.IMesh.Pass;
import ch.fhnw.ether.scene.mesh.MeshLibrary;
import ch.fhnw.ether.scene.mesh.geometry.DefaultGeometry;
import ch.fhnw.ether.scene.mesh.geometry.IGeometry;
import ch.fhnw.ether.scene.mesh.geometry.IGeometry.PrimitiveType;
import ch.fhnw.ether.scene.mesh.material.ColorMapMaterial;
import ch.fhnw.ether.scene.mesh.material.IMaterial;
import ch.fhnw.ether.view.IView;
import ch.fhnw.util.Viewport;
import ch.fhnw.util.color.RGBA;
import ch.fhnw.util.math.Vec3;

public class RayTracingRenderer implements IRenderer {
	private static final RGBA BACKGROUND_COLOR = RGBA.WHITE;

	private final List<RayTraceMesh> meshes = new ArrayList<>();

	private final ForwardRenderer renderer = new ForwardRenderer();
	private final Texture screenTexture = new Texture();
	private final IMesh plane = createScreenPlane(-1, -1, 2, 2, screenTexture);
	private int[] colors;
	private int w = 0, h = 0;
	private long n = 0;

	public RayTracingRenderer() {
		renderer.addMesh(plane);
	}

	@Override
	public void render(GL3 gl, IView view) {
		long t = System.currentTimeMillis();
		Viewport viewport = view.getViewport();
		if (viewport.w != w || viewport.h != h) {
			w = viewport.w;
			h = viewport.h;
			colors = new int[w * h];
		}
		ICamera camera = view.getCamera();
		ILight light = view.getController().getScene().getLights().get(0);
		Vec3 camPos = camera.getPosition();

		float planeWidth = (float) (2 * Math.tan(camera.getFov() / 2) * camera.getNear());
		float planeHeight = planeWidth / viewport.getAspect();

		float deltaX = planeWidth / w;
		float deltaY = planeHeight / h;

		Vec3 lookVector = camera.getTarget().subtract(camera.getPosition()).normalize();
		Vec3 upVector = camera.getUp().normalize();
		Vec3 sideVector = lookVector.cross(upVector).normalize();

		for (int j = -h / 2; j < h / 2; ++j) {
			for (int i = -w / 2; i < w / 2; ++i) {
				Vec3 x = sideVector.scale(i * deltaX);
				Vec3 y = upVector.scale(j * deltaY);
				Vec3 dir = lookVector.add(x).add(y);
				Ray ray = new Ray(camPos, dir);
				RGBA color = intersection(ray, light);
				colors[(j + h / 2) * w + (i + w / 2)] = color.toInt();
			}
		}

		screenTexture.setData(viewport.w, viewport.h, IntBuffer.wrap(colors), GL.GL_RGBA);
		
		renderer.render(gl, view);
		System.out.println((System.currentTimeMillis() - t) + "ms for " + ++n + "th frame");
	}

	@Override
	public void addMesh(IMesh mesh) {
		if (mesh instanceof RayTraceMesh)
			meshes.add((RayTraceMesh) mesh);
	}

	@Override
	public void removeMesh(IMesh mesh) {
		meshes.remove(mesh);
	}

	private RGBA intersection(Ray ray, ILight light) {

		// find nearest intersection point in scene
		IntersectResult nearest = new IntersectResult(null, null, BACKGROUND_COLOR, Float.POSITIVE_INFINITY);
		for (RayTraceMesh r : meshes) {
			IntersectResult intersect = r.intersect(ray);
			if (intersect.isValid() && intersect.dist < nearest.dist) {
				nearest = intersect;
			}
		}

		// no object found
		if (!nearest.isValid())
			return BACKGROUND_COLOR;

		// create vector which is sure over surface
		Vec3 position_ontop_surface = nearest.position.subtract(ray.direction.scale(0.01f));
		position_ontop_surface = position_ontop_surface.add(nearest.surface.getNormalAt(nearest.position).scale(0.0001f));

		float dist_to_light = light.getPosition().subtract(nearest.position).length();

		// check if path to light is clear
		Ray light_ray = new Ray(position_ontop_surface, light.getPosition().subtract(position_ontop_surface));
		for (RayTraceMesh r : meshes) {
			IntersectResult intersect = r.intersect(light_ray);
			if (intersect.isValid() && intersect.dist < dist_to_light) {
				return RGBA.BLACK;
			}
		}

		// diffuse color
		float f = Math.max(0, light_ray.direction.dot(nearest.surface.getNormalAt(nearest.position)));
		RGBA c = nearest.color;
		RGBA lc = light.getColor();

		return new RGBA(f * c.x * lc.x, f * c.y * lc.y, f * c.z * lc.z, c.w);
	}

	private static IMesh createScreenPlane(float x, float y, float w, float h, Texture texture) {
		IAttribute[] attribs = { IMaterial.POSITION_ARRAY, IMaterial.COLOR_MAP_ARRAY };
		float[] position = { x, y, 0, x + w, y, 0, x + w, y + h, 0, x, y, 0, x + w, y + h, 0, x, y + h, 0 };
		float[][] data = { position, MeshLibrary.DEFAULT_QUAD_TEX_COORDS };
		IGeometry geometry = new DefaultGeometry(PrimitiveType.TRIANGLES, attribs, data);
		
		return new DefaultMesh(new ColorMapMaterial(texture), geometry, Pass.DEVICE_SPACE_OVERLAY);
	}
}