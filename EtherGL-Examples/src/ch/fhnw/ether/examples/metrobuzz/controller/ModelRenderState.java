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

package ch.fhnw.ether.examples.metrobuzz.controller;

import ch.fhnw.ether.examples.metrobuzz.model.Model;
import ch.fhnw.ether.geom.RGBA;
import ch.fhnw.ether.render.IRenderable;
import ch.fhnw.ether.render.IRenderer;
import ch.fhnw.ether.render.IRenderer.Pass;
import ch.fhnw.ether.render.shader.builtin.Lines;
import ch.fhnw.ether.render.shader.builtin.Points;

public final class ModelRenderState {
	private static final RGBA NETWORK_NODE_COLOR = RGBA.GRAY;
	private static final RGBA NETWORK_EDGE_COLOR = RGBA.GRAY;
	private static final float NETWORK_POINT_SIZE = 4.0f;

	protected final Model model;
	
	private final IRenderable networkNodes;
	private final IRenderable networkEdges;
	private final IRenderable agentPaths;

	public ModelRenderState(Model model) {
		this.model = model;

		IRenderer renderer = model.getController().getRenderer();
		networkNodes = renderer.createRenderable(Pass.DEPTH, new Points(NETWORK_NODE_COLOR, NETWORK_POINT_SIZE, 0), model.getNetworkGeometry());
		networkEdges = renderer.createRenderable(Pass.DEPTH, new Lines(NETWORK_EDGE_COLOR), model.getNetworkGeometry());
		agentPaths = renderer.createRenderable(Pass.TRANSPARENCY, new Lines(), model.getAgentGeometries());
		
		renderer.addRenderables(networkNodes, networkEdges, agentPaths);
		
		updateNetwork();
		updateAgents();
	}

	public void updateNetwork() {
		networkNodes.requestUpdate();
		networkEdges.requestUpdate();
	}
	
	public void updateAgents() {
		agentPaths.requestUpdate();
	}
}
