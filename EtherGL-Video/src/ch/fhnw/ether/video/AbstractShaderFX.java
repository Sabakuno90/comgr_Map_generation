package ch.fhnw.ether.video;

import javax.media.opengl.GL;

import ch.fhnw.ether.media.FXParameter;

public abstract class AbstractShaderFX extends AbstractFX {
	protected final GL            gl = null;
	
	protected AbstractShaderFX(int width, int height, FXParameter ... parameters) {
		super(width, height, parameters);
	}
}