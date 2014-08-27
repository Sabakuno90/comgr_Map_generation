/*
 * Copyright (c) 2013 - 2014 FHNW & ETH Zurich (Stefan Muller Arisona & Simon Schubiger)
 * Copyright (c) 2013 - 2014 Stefan Muller Arisona & Simon Schubiger
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

import ch.fhnw.ether.geom.Mat4;
import ch.fhnw.ether.render.AbstractRenderer;
import ch.fhnw.ether.render.attribute.IAttribute.ISuppliers;
import ch.fhnw.ether.render.attribute.builtin.ProjMatrixUniform;
import ch.fhnw.ether.render.attribute.builtin.ViewMatrixUniform;
import ch.fhnw.ether.view.IView;

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
	private final RenderState state = new RenderState();
	private final Mat4 projMatrix2D = Mat4.identityMatrix();
	private final Mat4 viewMatrix2D = Mat4.identityMatrix();

	public ForwardRenderer() {
	}

	@Override
	public void render(GL3 gl, IView view) {
		update(gl);
		
		Mat4 projMatrix = view.getCamera().getProjMatrix();
		Mat4 viewMatrix = view.getCamera().getViewMatrix();
		
		state.setMatrices(projMatrix, viewMatrix);
		
		// ---- 1. DEPTH PASS (DEPTH WRITE&TEST ENABLED, BLEND OFF)
		gl.glEnable(GL.GL_DEPTH_TEST);
		gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
		gl.glPolygonOffset(1, 3);
		renderPass(gl, view, state, Pass.DEPTH);
		gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);

		// ---- 2. TRANSPARENCY PASS (DEPTH WRITE DISABLED, DEPTH TEST ENABLED, BLEND ON)
		gl.glEnable(GL.GL_BLEND);
		gl.glDepthMask(false);
		renderPass(gl, view, state, Pass.TRANSPARENCY);

		// ---- 3. OVERLAY PASS (DEPTH WRITE&TEST DISABLED, BLEND ON)
		gl.glDisable(GL.GL_DEPTH_TEST);
		renderPass(gl, view, state, Pass.OVERLAY);

		// ---- 4. DEVICE SPACE OVERLAY (DEPTH WRITE&TEST DISABLED, BLEND ON)
		state.setMatrices(projMatrix2D, viewMatrix2D);

		projMatrix2D.identity();
		renderPass(gl, view, state, Pass.DEVICE_SPACE_OVERLAY);

		// ---- 5. SCREEN SPACE OVERLAY (DEPTH WRITE&TEST DISABLED, BLEND ON)
		projMatrix2D.ortho(0, view.getViewport().w, view.getViewport().h, 0, -1, 1);
		renderPass(gl, view, state, Pass.SCREEN_SPACE_OVERLAY);

		// ---- 6. CLEANUP: RETURN TO DEFAULTS
		gl.glDisable(GL.GL_BLEND);
		gl.glDepthMask(true);
	}

	@Override
	public void getAttributeSuppliers(ISuppliers dst) {
		dst.add(ProjMatrixUniform.supply(() -> state.projMatrix));
		dst.add(ViewMatrixUniform.supply(() -> state.viewMatrix));
	}
}