package ch.ethz.ether.render;

import javax.media.opengl.GL3;

import ch.ethz.ether.geom.Mat4;
import ch.ethz.ether.render.IRenderGroup.Pass;
import ch.ethz.ether.view.IView;

public abstract class AbstractRenderer implements IRenderer {

    protected void updateGroups(GL3 gl, IRenderer renderer) {
        ((RenderGroups) GROUPS).update(gl, renderer);
    }

    protected void renderGroups(GL3 gl, IRenderer renderer, IView view, Mat4 projMatrix, Mat4 viewMatrix, Pass pass) {
        ((RenderGroups) GROUPS).render(gl, renderer, view, projMatrix, viewMatrix, pass);
    }
}