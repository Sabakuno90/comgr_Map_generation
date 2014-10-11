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

package ch.fhnw.ether.examples.metrobuzz.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.fhnw.ether.camera.ICamera;
import ch.fhnw.ether.render.IRenderable;
import ch.fhnw.ether.render.IRenderer;
import ch.fhnw.ether.render.IRenderer.Pass;
import ch.fhnw.ether.render.attribute.IAttribute.PrimitiveType;
import ch.fhnw.ether.render.shader.builtin.LineShader;
import ch.fhnw.ether.render.shader.builtin.PointShader;
import ch.fhnw.ether.scene.IScene;
import ch.fhnw.ether.scene.light.ILight;
import ch.fhnw.ether.scene.mesh.GenericMesh;
import ch.fhnw.ether.scene.mesh.IMesh;
import ch.fhnw.ether.scene.mesh.geometry.IGeometry;
import ch.fhnw.ether.scene.mesh.material.ColorMaterial;
import ch.fhnw.util.color.RGBA;
import ch.fhnw.util.math.geometry.BoundingBox;
import ch.fhnw.util.math.geometry.I3DObject;

public class Scene implements IScene {
	private static final float[] ACTIVITY_COLOR = { 1f, 0f, 0f, 0.2f };
	private static final float[] TRIP_COLOR = { 0f, 1f, 0f, 0.2f };

	private final List<Node> nodes = new ArrayList<>();
	private final List<Link> links = new ArrayList<>();

	private final Map<String, Node> idToNode = new HashMap<>();
	private final Map<String, Link> idToLink = new HashMap<>();

	private final List<Agent> agents = new ArrayList<>();

	private List<GenericMesh> agentGeometries;
	private GenericMesh networkGeometryPoints;
	private GenericMesh networkGeometryLines;
	private ICamera camera;
	private IRenderer renderer;
	private IRenderable agent_renderable;
	private IRenderable net_renderable;

	public Scene(ICamera camera) {
		this.camera = camera;
	}

	public List<Node> getNodes() {
		return nodes;
	}

	public Node getNode(String id) {
		return idToNode.get(id);
	}

	public void addNode(Node node) {
		nodes.add(node);
		idToNode.put(node.getId(), node);
	}

	public List<Link> getLinks() {
		return links;
	}

	public Link getLink(String id) {
		return idToLink.get(id);
	}

	public void addLink(Link edge) {
		links.add(edge);
		idToLink.put(edge.getId(), edge);
	}

	public List<Agent> getAgents() {
		return agents;
	}

	public void addAgent(Agent agent) {
		agents.add(agent);
	}

	/**
	 * Normalize the model into a [-1,1][-1,1] area (we don't deal with time here)
	 */
	public void normalize() {
		// first determine overall scale of model, so we can normalize
		BoundingBox bounds = new BoundingBox();
		for (Node node : nodes) {
			bounds.add(node.getX(), node.getY(), 0);
		}

		// update node "coordinates" to [-1,1] (scale uniformly)
		for (Node node : nodes) {
			float scale = Math.max(bounds.getExtentX(), bounds.getExtentY());
			node.setX((node.getX() - bounds.getCenterX()) / scale);
			node.setY((node.getY() - bounds.getCenterY()) / scale);
		}
	}

	public static void printAgent(Agent agent) {
		System.out.println(agent);
		for (Activity activity : agent.getActivities()) {
			System.out.println(activity);
			Trip trip = activity.getTrip();
			if (trip != null) {
				System.out.println(trip);
				for (Link link : trip.getLinks()) {
					System.out.println("- " + link);
				}
			}
		}
		System.out.println();
	}

	private void createGeometries() {
		agentGeometries = new ArrayList<>();

		// add network
		int i = 0;
		int j = 0;

		float[] networkNodes = new float[nodes.size() * 3];
		for (Node node : nodes) {
			networkNodes[i++] = node.getX();
			networkNodes[i++] = node.getY();
			networkNodes[i++] = 0;
		}

		i = 0;
		float[] networkEdges = new float[links.size() * 6];
		for (Link link : links) {
			networkEdges[i++] = link.getFromNode().getX();
			networkEdges[i++] = link.getFromNode().getY();
			networkEdges[i++] = 0;
			networkEdges[i++] = link.getToNode().getX();
			networkEdges[i++] = link.getToNode().getY();
			networkEdges[i++] = 0;
		}

		networkGeometryPoints = new GenericMesh(PrimitiveType.POINT);
		networkGeometryLines = new GenericMesh(PrimitiveType.LINE);
		networkGeometryPoints.setGeometry(networkNodes);
		networkGeometryLines.setGeometry(networkEdges);

		// add agents (count number of paths first, then add);

		for (Agent agent : agents) {
			int numPaths = 0;
			for (Activity activity : agent.getActivities()) {
				// one path for activity + one for each link
				numPaths++;
				Trip trip = activity.getTrip();
				if (trip == null)
					continue;
				switch (trip.getMode()) {
				case WALK:
				case TRANSIT_WALK:
					numPaths++;
				default:
					numPaths += trip.getLinks().size();
				}
			}
			float[] agentEdges = new float[6 * numPaths];
			float[] agentColors = new float[8 * numPaths];

			i = 0;
			j = 0;
			for (Activity activity : agent.getActivities()) {
				agentEdges[i++] = activity.getLocation().getX();
				agentEdges[i++] = activity.getLocation().getY();
				agentEdges[i++] = normTime(activity.getStartTime());
				agentEdges[i++] = activity.getLocation().getX();
				agentEdges[i++] = activity.getLocation().getY();
				agentEdges[i++] = normTime(activity.getEndTime());
				agentColors[j++] = ACTIVITY_COLOR[0];
				agentColors[j++] = ACTIVITY_COLOR[1];
				agentColors[j++] = ACTIVITY_COLOR[2];
				agentColors[j++] = ACTIVITY_COLOR[3];
				agentColors[j++] = ACTIVITY_COLOR[0];
				agentColors[j++] = ACTIVITY_COLOR[1];
				agentColors[j++] = ACTIVITY_COLOR[2];
				agentColors[j++] = ACTIVITY_COLOR[3];
				Trip trip = activity.getTrip();
				if (trip == null)
					continue;

				// XXX: note there's some weirdness with the links, we're currently just using fromNode from each link
				switch (trip.getMode()) {
				case WALK:
				case TRANSIT_WALK: {
					Link startLink = trip.getLinks().get(0);
					Link endLink = trip.getLinks().get(1);
					agentEdges[i++] = startLink.getFromNode().getX();
					agentEdges[i++] = startLink.getFromNode().getY();
					agentEdges[i++] = normTime(trip.getStartTime());
					agentEdges[i++] = endLink.getFromNode().getX();
					agentEdges[i++] = endLink.getFromNode().getY();
					agentEdges[i++] = normTime(trip.getEndTime());
					agentColors[j++] = TRIP_COLOR[0];
					agentColors[j++] = TRIP_COLOR[1];
					agentColors[j++] = TRIP_COLOR[2];
					agentColors[j++] = TRIP_COLOR[3];
					agentColors[j++] = TRIP_COLOR[0];
					agentColors[j++] = TRIP_COLOR[1];
					agentColors[j++] = TRIP_COLOR[2];
					agentColors[j++] = TRIP_COLOR[3];
				}
					break;
				default: {
					List<Link> links = trip.getLinks();
					if (links.size() == 1) {
						System.out.println("only one link, skipping");
						continue;
					}
					float startTime = trip.getStartTime();
					float deltaTime = (trip.getEndTime() - trip.getStartTime()) / (trip.getLinks().size() - 1);
					for (int index = 0; index < links.size(); ++index) {
						Link link = links.get(index);
						agentEdges[i++] = link.getFromNode().getX();
						agentEdges[i++] = link.getFromNode().getY();
						agentEdges[i++] = normTime(startTime);
						agentColors[j++] = TRIP_COLOR[0];
						agentColors[j++] = TRIP_COLOR[1];
						agentColors[j++] = TRIP_COLOR[2];
						agentColors[j++] = TRIP_COLOR[3];
						if (index > 0 && index < links.size() - 1) {
							agentEdges[i++] = link.getFromNode().getX();
							agentEdges[i++] = link.getFromNode().getY();
							agentEdges[i++] = normTime(startTime);
							agentColors[j++] = TRIP_COLOR[0];
							agentColors[j++] = TRIP_COLOR[1];
							agentColors[j++] = TRIP_COLOR[2];
							agentColors[j++] = TRIP_COLOR[3];
						}
						startTime += deltaTime;
					}
				}
				}
			}
			GenericMesh geometry = new GenericMesh(PrimitiveType.LINE);
			geometry.setGeometry(agentEdges, agentColors);
			agentGeometries.add(geometry);
		}
	}

	private static float normTime(float time) {
		return time / (24 * 60 * 60);
	}

	@Override
	public List<? extends I3DObject> getObjects() {
		return getMeshes();
	}

	@Override
	public List<IMesh> getMeshes() {
		createGeometries();
		List<IMesh> l = new ArrayList<>(agentGeometries);
		l.add(networkGeometryPoints);
		l.add(networkGeometryLines);
		return l;
	}

	@Override
	public void setRenderer(IRenderer renderer) {
		if (this.renderer == renderer)
			return;
		this.renderer = renderer;

		renderer.removeRenderables(agent_renderable, net_renderable);
		createGeometries();

		List<IGeometry> line_geometries = agentGeometries.stream().map((x) -> {
			return x.getGeometry();
		}).collect(Collectors.toList());
		line_geometries.add(networkGeometryLines.getGeometry());
		agent_renderable = renderer.createRenderable(Pass.DEPTH, new LineShader(true), null, line_geometries);

		List<IGeometry> point_geometries = new ArrayList<>(1);
		point_geometries.add(networkGeometryPoints.getGeometry());
		net_renderable = renderer.createRenderable(Pass.DEPTH, new PointShader(false), new ColorMaterial(RGBA.YELLOW), point_geometries);

		renderer.addRenderables(agent_renderable, net_renderable);
	}

	@Override
	public List<ICamera> getCameras() {
		return Collections.singletonList(camera);
	}

	@Override
	public List<ILight> getLights() {
		return Collections.emptyList();
	}

	@Override
	public void renderUpdate() {
		if (agent_renderable != null)
			agent_renderable.requestUpdate();
		if (net_renderable != null)
			net_renderable.requestUpdate();
	}
}
