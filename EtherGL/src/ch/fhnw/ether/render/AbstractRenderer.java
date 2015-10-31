/*
 * Copyright (c) 2013 - 2015 Stefan Muller Arisona, Simon Schubiger, Samuel von Stachelski
 * Copyright (c) 2013 - 2015 FHNW & ETH Zurich
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

package ch.fhnw.ether.render;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.jogamp.opengl.GL3;

import ch.fhnw.ether.render.forward.ShadowVolumes;
import ch.fhnw.ether.scene.attribute.IAttribute;
import ch.fhnw.ether.scene.mesh.IMesh.Flag;
import ch.fhnw.ether.scene.mesh.IMesh.Queue;

public abstract class AbstractRenderer implements IRenderer {
	public static final class RenderGlobals {
		public final Map<IAttribute, Supplier<?>> attributes = new IdentityHashMap<>();
		public final ViewInfo viewInfo = new ViewInfo();
		public final LightInfo lightInfo = new LightInfo();
		
		private RenderGlobals() {
			viewInfo.getAttributes(attributes);
			lightInfo.getAttributes(attributes);
		}
	}

	protected final RenderGlobals globals = new RenderGlobals();

	private ShadowVolumes shadowVolumes;

	public AbstractRenderer() {
	}

	protected void renderObjects(GL3 gl, IRenderState state, Queue pass, boolean interactive) {
		for (Renderable renderable : state.getRenderables()) {
			if (renderable.containsFlag(Flag.INTERACTIVE_VIEWS_ONLY) && !interactive)
				continue;
			if (renderable.getQueue() == pass) {
				renderable.render(gl);
			}
		}
	}

	protected void renderShadowVolumes(GL3 gl, IRenderState state, Queue pass, boolean interactive) {
		if (shadowVolumes == null) {
			shadowVolumes = new ShadowVolumes(globals.attributes);
		}
		shadowVolumes.render(gl, pass, interactive, state.getRenderables(), globals.lightInfo.getNumLights());
	}
}
