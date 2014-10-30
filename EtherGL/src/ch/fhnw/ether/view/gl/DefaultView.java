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

package ch.fhnw.ether.view.gl;

import javax.media.nativewindow.util.Point;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

import ch.fhnw.ether.camera.CameraMatrices;
import ch.fhnw.ether.camera.ICamera;
import ch.fhnw.ether.controller.IController;
import ch.fhnw.ether.ui.UI;
import ch.fhnw.ether.view.IView;
import ch.fhnw.util.Viewport;
import ch.fhnw.util.math.Mat4;

import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.MouseEvent;

/**
 * Default view class that implements some basic functionality. Use as base for more complex implementations.
 * 
 * Thread safety: getCameraMatrices & getViewport are thread safe.
 *
 * @author radar
 */
public class DefaultView implements IView {

	private final ViewType viewType;

	private NEWTWindow window;

	private IController controller;

	private final Object lock = new Object();

	private ICamera camera;

	private CameraMatrices cameraMatrices = null;
	private boolean cameraLocked = false;

	private Viewport viewport = new Viewport(0, 0, 1, 1);

	private boolean enabled = true;

	public DefaultView(IController controller, int x, int y, int w, int h, ViewType viewType, String title, ICamera camera) {
		this.viewType = viewType;
		this.window = new NEWTWindow(w, h, title);
		window.setView(this);
		Point p = window.getPosition();
		if (x != -1)
			p.setX(x);
		if (y != -1)
			p.setY(y);
		window.setPosition(p);
		this.controller = controller;
		setCamera(camera);
	}

	@Override
	public GLAutoDrawable getDrawable() {
		return window.getDrawable();
	}

	@Override
	public final IController getController() {
		return controller;
	}

	@Override
	public final ICamera getCamera() {
		return camera;
	}

	@Override
	public final void setCamera(ICamera camera) {
		synchronized (lock) {
			if (this.camera != null) this.camera.removeUpdateListener(this);
			this.camera = camera;
			if (camera != null) this.camera.addUpdateListener(this);
		}
	}

	@Override
	public final CameraMatrices getCameraMatrices() {
		synchronized (lock) {
			ICamera c = camera;
			if (cameraMatrices == null)
				cameraMatrices = new CameraMatrices(c.getPosition(), c.getTarget(), c.getUp(), c.getFov(), c.getNear(), c.getFar(), viewport.getAspect());
			return cameraMatrices;
		}
	}
	
	@Override
	public void setCameraMatrices(Mat4 viewMatrix, Mat4 projMatrix) {
		synchronized (lock) {
			if (viewMatrix == null && projMatrix == null) {
				cameraMatrices = null;
				cameraLocked = false;
			} else {
				cameraMatrices = new CameraMatrices(viewMatrix, projMatrix);
				cameraLocked = true;
			}
		}
	}

	@Override
	public final Viewport getViewport() {
		synchronized (lock) {
			return viewport;
		}
	}

	@Override
	public final ViewType getViewType() {
		return viewType;
	}

	@Override
	public final boolean isEnabled() {
		return enabled;
	}

	@Override
	public final void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public final boolean isCurrent() {
		return getController().getCurrentView() == this;
	}

	@Override
	public final void repaint() {
		getController().repaintView(this);
	}

	
	@Override
	public void requestUpdate(Object source) {
		if (source instanceof ICamera) {
			synchronized (lock) {
				if (!cameraLocked)
					cameraMatrices = null;
			}
			getController().getCurrentTool().refresh(this);
			repaint();
			
		}
	}

	// GLEventListener implementation

	@Override
	public final void init(GLAutoDrawable drawable) {
		try {
			GL gl = drawable.getGL();

			gl.glClearColor(0.1f, 0.2f, 0.3f, 1.0f);
			gl.glClearDepth(1.0f);

			gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public final void display(GLAutoDrawable drawable) {
		try {
			GL gl = drawable.getGL();

			gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);

			if (!isEnabled())
				return;

			// fetch viewport
			int[] vp = new int[4];
			gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
			viewport = new Viewport(vp[0], vp[1], vp[2], vp[3]);

			// render everything

			// repaint UI surface to texture if necessary (FIXME: should this be done on model or render thread?)
			UI ui = getController().getUI();
			if (ui != null)
				ui.update();

			getController().getRenderer().render(gl.getGL3(), this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public final void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		try {
			GL gl = drawable.getGL();

			if (height == 0)
				height = 1; // prevent divide by zero
			gl.glViewport(0, 0, width, height);
			synchronized (lock) {
				viewport = new Viewport(0, 0, width, height);
				if (!cameraLocked)
					cameraMatrices = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public final void dispose(GLAutoDrawable drawable) {
		try {
			window.dispose();
			controller.removeView(this);
			setCamera(null);
			window = null;
			controller = null;
			camera = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// key listener

	@Override
	public void keyPressed(KeyEvent e) {
		try {
			controller.keyPressed(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		try {
			controller.keyReleased(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	// mouse listener

	@Override
	public void mouseEntered(MouseEvent e) {
		try {
			controller.mouseEntered(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void mouseExited(MouseEvent e) {
		try {
			controller.mouseExited(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		try {
			window.requestFocus();
			controller.mousePressed(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		try {
			controller.mouseReleased(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		try {
			controller.mouseClicked(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	// mouse motion listener

	@Override
	public void mouseMoved(MouseEvent e) {
		try {
			controller.mouseMoved(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		try {
			controller.mouseDragged(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	// mouse wheel listener

	@Override
	public void mouseWheelMoved(MouseEvent e) {
		try {
			controller.mouseWheelMoved(e, this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}