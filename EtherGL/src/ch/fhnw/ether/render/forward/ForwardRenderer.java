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

package ch.fhnw.ether.render.forward;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import ch.fhnw.ether.render.AbstractRenderer;
import ch.fhnw.ether.render.variable.builtin.NormalMatrixUniform;
import ch.fhnw.ether.render.variable.builtin.ProjMatrixUniform;
import ch.fhnw.ether.render.variable.builtin.ViewMatrixUniform;
import ch.fhnw.ether.scene.attribute.IAttributeProvider;
import ch.fhnw.ether.scene.camera.CameraMatrices;
import ch.fhnw.ether.scene.mesh.IMesh.Pass;
import ch.fhnw.ether.view.IView;
import ch.fhnw.util.math.Mat3;
import ch.fhnw.util.math.Mat4;

/*
 * General flow:
 * - foreach viewport
 * -- only use geometry assigned to this viewport
 * 
 * - foreach pass
 * -- setup opengl params specific to pass
 * 
 * - foreach material
 * -- enable shader
 * -- write uniforms
 * 
 * - foreach material instance (texture set + uniforms)
 * -- setup texture
 * -- write uniforms
 * -- refresh buffers
 * 
 * - foreach buffer (assembled objects)
 * -- setup buffer
 * -- draw
 */

/**
 * Simple and straightforward forward renderer.
 *
 * @author radar
 */
public class ForwardRenderer extends AbstractRenderer {
	
	private static class ViewState implements IAttributeProvider {
		private static final Mat3 ID_3X3 = Mat3.identityMatrix();
		private static final Mat4 ID_4X4 = Mat4.identityMatrix();

		private Mat4 viewMatrix;
		private Mat4 projMatrix;
		private Mat3 normalMatrix;

		void setCameraSpace(IView view) {
			viewMatrix = view.getCameraMatrices().getViewMatrix();
			projMatrix = view.getCameraMatrices().getProjMatrix();
			normalMatrix = view.getCameraMatrices().getNormalMatrix();
		}
		
		void setOrthoScreenSpace(IView view) {
			viewMatrix = ID_4X4;
			normalMatrix = ID_3X3;
			projMatrix = Mat4.ortho(0, view.getViewport().w, view.getViewport().h, 0, -1, 1);			
		}

		void setOrthoDeviceSpace(IView view) {
			viewMatrix = projMatrix = ID_4X4;
			normalMatrix = ID_3X3;
		}
		
		@Override
		public void getAttributes(IAttributes attributes) {
			attributes.provide(ProjMatrixUniform.ID, () -> projMatrix);
			attributes.provide(ViewMatrixUniform.ID, () -> viewMatrix);
			attributes.provide(NormalMatrixUniform.ID, () -> normalMatrix);
		}
	}

	private final ViewState state = new ViewState();

	public ForwardRenderer() {
		addAttributeProvider(state);
	}
	
	@Override
	public void render(GL3 gl, IView view) {
		CameraMatrices cameraMatrices = view.getCameraMatrices();
		boolean interactive = view.getConfig().getViewType() == IView.ViewType.INTERACTIVE_VIEW;
		
		
		update(gl, cameraMatrices);

		state.setCameraSpace(view);
		
		// ---- 1. DEPTH PASS (DEPTH WRITE&TEST ENABLED, BLEND OFF)
		gl.glEnable(GL.GL_DEPTH_TEST);
		gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
		gl.glPolygonOffset(1, 3);
		renderObjects(gl, Pass.DEPTH, interactive);
		gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
		
		if (false) renderShadowVolumes(gl, Pass.DEPTH, interactive);

		// ---- 2. TRANSPARENCY PASS (DEPTH WRITE DISABLED, DEPTH TEST ENABLED, BLEND ON)
		gl.glEnable(GL.GL_BLEND);
		gl.glDepthMask(false);
		renderObjects(gl, Pass.TRANSPARENCY, interactive);

		// ---- 3. OVERLAY PASS (DEPTH WRITE&TEST DISABLED, BLEND ON)
		gl.glDisable(GL.GL_DEPTH_TEST);
		renderObjects(gl, Pass.OVERLAY, interactive);

		// ---- 4. DEVICE SPACE OVERLAY (DEPTH WRITE&TEST DISABLED, BLEND ON)
		state.setOrthoDeviceSpace(view);
		renderObjects(gl, Pass.DEVICE_SPACE_OVERLAY, interactive);

		// ---- 5. SCREEN SPACE OVERLAY (DEPTH WRITE&TEST DISABLED, BLEND ON)
		state.setOrthoScreenSpace(view);
		renderObjects(gl, Pass.SCREEN_SPACE_OVERLAY, interactive);

		// ---- 6. CLEANUP: RETURN TO DEFAULTS
		gl.glDisable(GL.GL_BLEND);
		gl.glDepthMask(true);
	}
}
