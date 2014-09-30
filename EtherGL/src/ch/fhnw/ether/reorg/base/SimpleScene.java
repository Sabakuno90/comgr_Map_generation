package ch.fhnw.ether.reorg.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ch.fhnw.ether.render.IRenderable;
import ch.fhnw.ether.render.IRenderer;
import ch.fhnw.ether.render.IRenderer.Pass;
import ch.fhnw.ether.render.Renderable;
import ch.fhnw.ether.render.attribute.IUniformAttributeProvider;
import ch.fhnw.ether.render.attribute.IAttribute.ISuppliers;
import ch.fhnw.ether.render.shader.IShader;
import ch.fhnw.ether.reorg.api.IGeometry;
import ch.fhnw.ether.reorg.api.ILight;
import ch.fhnw.ether.reorg.api.IMaterial;
import ch.fhnw.ether.reorg.api.IMesh;
import ch.fhnw.ether.reorg.api.IScene;

//only for testing purposes
public class SimpleScene implements IScene{
	
	private final List<IMesh> meshes = Collections.synchronizedList(new ArrayList<>(10));
	private final List<ILight> lights = Collections.synchronizedList(new ArrayList<>(3));
	
	IUniformAttributeProvider uniforms = new IUniformAttributeProvider() {
		@Override
		public void getAttributeSuppliers(ISuppliers dst) {
			
			Set<IMaterial> materials = new HashSet<>();
			
			//for(IMesh m : meshes) {
			for(int i=0; i<meshes.size(); ++i) {
				IMesh m = meshes.get(i);
				IMaterial material = m.getMaterial();
				if(!materials.contains(m)) {
					materials.add(material);
					m.getMaterial().getAttributeSuppliers(dst);
				}
			}
		}
	};

	public SimpleScene() {

	}
	
	public boolean addMesh(IMesh mesh) {
		return meshes.add(mesh);
	}

	public boolean removeMesh(IMesh mesh) {
		return meshes.remove(mesh);
	}

	@Override
	public List<IMesh> getObjects() {
		return meshes;
	}

	public boolean addLight(ILight light) {
		return lights.add(light);
	}

	public boolean removeLight(ILight light) {
		return lights.remove(light);
	}

	public List<ILight> getLights() {
		return lights;
	}

	public IRenderable[] getRenderables(IRenderer r, IShader s) {
		
		final List<IGeometry> geo = Collections.synchronizedList(meshes.stream().map((x) -> {return x.getGeometry();}).collect(Collectors.toList()));
		
		IRenderable ret = new Renderable(Pass.DEPTH, EnumSet.noneOf(IRenderer.Flag.class), s, uniforms, geo);
		
		return new IRenderable[]{ret};
	}

}
