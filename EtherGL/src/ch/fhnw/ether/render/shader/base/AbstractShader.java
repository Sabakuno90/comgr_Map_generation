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

package ch.fhnw.ether.render.shader.base;

import java.util.ArrayList;
import java.util.List;

import ch.fhnw.ether.render.IVertexBuffer;
import ch.fhnw.ether.render.gl.Program;
import ch.fhnw.ether.render.shader.IShader;
import ch.fhnw.ether.render.variable.IShaderArray;
import ch.fhnw.ether.render.variable.IShaderUniform;
import ch.fhnw.ether.scene.mesh.geometry.IGeometry.Primitive;
import ch.fhnw.util.Log;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

public abstract class AbstractShader implements IShader {
	private static final Log log = Log.create();
	
	public static final String INLINE = "/*__inline__*/\n"; 

	// important: keep this in sync with PrimitiveType enum
	public static final int[] MODE = { GL.GL_POINTS, GL.GL_LINES, GL.GL_TRIANGLES };

	private final Class<?>  root;
	private final String    name;
	private final String[]  source;
	private final Primitive type;
	private       Program   program;

	private List<IShaderUniform<?>> uniforms = new ArrayList<>();
	private List<IShaderArray<?>> arrays = new ArrayList<>();

	protected AbstractShader(Class<?> root, String name, String source, Primitive type) {
		this.root   = root;
		this.name   = name;
		this.source = new String[] {source};
		this.type   = type;
	}

	protected AbstractShader(Class<?> root, String name, String vert, String frag, String geom, Primitive type) {
		this.root   = root;
		this.name   = name;
		this.source = new String[] {vert, frag, geom};
		this.type   = type;
	}

	@Override
	public final String id() {
		return name;
	}

	@Override
	public final void update(GL3 gl, Object[] uniformData) {
		if (program == null) {
			String vertShader;
			String fragShader;
			String geomShader;
			if(source.length == 1) {
				vertShader = "glsl/" + source[0] + "_vert.glsl";
				fragShader = "glsl/" + source[0] + "_frag.glsl";
				geomShader = "glsl/" + source[0] + "_geom.glsl";
			} else {
				vertShader = INLINE + source[0];
				fragShader = INLINE + source[1];
				geomShader = INLINE + source[2];
			}
			try {
				program = Program.create(gl, root, vertShader, fragShader, geomShader, System.err);
			} catch (Throwable t) {
				log.severe("cannot create glsl program. exiting.", t);
				System.exit(1);
			}
		}
		uniforms.forEach(attr -> attr.update(uniformData));
	}

	@Override
	public final void enable(GL3 gl) {
		// enable program & uniforms (set uniforms, enable textures, change gl state)
		program.enable(gl);
		uniforms.forEach(attr -> attr.enable(gl, program));
	}

	@Override
	public final void render(GL3 gl, IVertexBuffer buffer) {
		buffer.bind(gl);
		arrays.forEach(attr -> attr.enable(gl, program, buffer));

		int mode = MODE[type.ordinal()];
		gl.glDrawArrays(mode, 0, buffer.getNumVertices());

		arrays.forEach(attr -> attr.disable(gl, program, buffer));
		buffer.unbind(gl);
	}

	@Override
	public final void disable(GL3 gl) {
		// disable program and uniforms (disable textures, restore gl state)
		uniforms.forEach(attr -> attr.disable(gl, program));
		program.disable(gl);
	}

	@Override
	public final List<IShaderUniform<?>> getUniforms() {
		return uniforms;
	}

	@Override
	public List<IShaderArray<?>> getArrays() {
		return arrays;
	}

	protected final void addUniform(IShaderUniform<?> uniform) {
		uniforms.add(uniform);
	}

	protected final void addArray(IShaderArray<?> array) {
		arrays.add(array);
	}

	@Override
	public String toString() {
		return id();
	}
}
