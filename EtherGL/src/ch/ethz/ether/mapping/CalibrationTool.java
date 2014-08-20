/*
 * Copyright (c) 2013 - 2014, ETH Zurich & FHNW (Stefan Muller Arisona)
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
 *  Neither the name of ETH Zurich nor the names of its contributors may be
 *   used to endorse or promote products derived from this software without
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

package ch.ethz.ether.mapping;

import ch.ethz.ether.geom.ProjectionUtil;
import ch.ethz.ether.geom.Vec3;
import ch.ethz.ether.gl.Viewport;
import ch.ethz.ether.render.AbstractRenderGroup;
import ch.ethz.ether.render.IRenderGroup;
import ch.ethz.ether.render.IRenderGroup.Pass;
import ch.ethz.ether.render.IRenderGroup.Source;
import ch.ethz.ether.render.IRenderGroup.Type;
import ch.ethz.ether.render.IRenderer;
import ch.ethz.ether.render.util.Primitives;
import ch.ethz.ether.scene.AbstractScene;
import ch.ethz.ether.scene.IScene;
import ch.ethz.ether.tools.AbstractTool;
import ch.ethz.ether.view.IView;
import ch.ethz.util.IAddOnlyFloatList;
import ch.ethz.util.PreferencesStore;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public final class CalibrationTool extends AbstractTool {
    private static final String[] HELP = {
            "Calibration Tool for 3D Mapping",
            "",
            "[0] Return",
            "",
            "[C] Clear Calibration",
            "[L] Load Calibration",
            "[S] Save Calibration",
            "[DEL] Clear Current Calibration Point",
    };

    public static final double MAX_CALIBRATION_ERROR = 0.5;

    public static final float[] MODEL_COLOR = {1.0f, 1.0f, 1.0f, 1.0f};
    public static final float[] CALIBRATION_COLOR_UNCALIBRATED = {1.0f, 1.0f, 0.0f, 1.0f};
    public static final float[] CALIBRATION_COLOR_CALIBRATED = {0.0f, 1.0f, 0.0f, 1.0f};

    private static final float POINT_SIZE = 10;
    private static final float CROSSHAIR_SIZE = 20;

    private final ICalibrator calibrator = new BimberRaskarCalibrator();
    private final ICalibrationModel model;

    private Map<IView, CalibrationContext> contexts = new HashMap<>();

    private IRenderGroup modelPoints = new AbstractRenderGroup(Source.TOOL, Type.POINTS, Pass.OVERLAY) {
        @Override
        public void getVertices(IAddOnlyFloatList dst) {
            dst.add(model.getCalibrationVertices());
        }

        @Override
        public float[] getColor() {
            return MODEL_COLOR;
        }

        @Override
        public float getPointSize() {
            return POINT_SIZE;
        }
    };

    private IRenderGroup modelLines = new AbstractRenderGroup(Source.TOOL, Type.LINES, Pass.OVERLAY) {
        @Override
        public void getVertices(IAddOnlyFloatList dst) {
            dst.add(model.getCalibrationLines());
        }

        @Override
        public float[] getColor() {
            return MODEL_COLOR;
        }
    };

    private IRenderGroup calibrationPoints = new AbstractRenderGroup(Source.TOOL, Type.POINTS, Pass.DEVICE_SPACE_OVERLAY) {
        @Override
        public void getVertices(IAddOnlyFloatList dst) {
            IView view = getScene().getCurrentView();
            if (view == null)
                return;
            for (Vec3 v : getContext(view).projectedVertices) {
                dst.add(v.x, v.y, v.z);
            }
        }

        @Override
        public float[] getColor() {
            IView view = getScene().getCurrentView();
            if (view == null)
                return MODEL_COLOR;
            return getContext(view).calibrated ? CALIBRATION_COLOR_CALIBRATED : CALIBRATION_COLOR_UNCALIBRATED;
        }

        @Override
        public float getPointSize() {
            return 10;
        }
    };

    private IRenderGroup calibrationLines = new AbstractRenderGroup(Source.TOOL, Type.LINES, Pass.DEVICE_SPACE_OVERLAY) {
        @Override
        public void getVertices(IAddOnlyFloatList dst) {
            IView view = getScene().getCurrentView();
            if (view == null)
                return;

            CalibrationContext context = getContext(view);
            for (int i = 0; i < context.projectedVertices.size(); ++i) {
                Vec3 a = context.modelVertices.get(i);
                Vec3 v = ProjectionUtil.projectToDevice(view, a);
                if (v == null)
                    continue;
                a = context.projectedVertices.get(i);
                Primitives.addLine(dst, v.x, v.y, v.z, a.x, a.y, a.z);

                if (i == context.currentSelection) {
                    Viewport viewport = view.getViewport();
                    Primitives.addLine(dst, a.x - CROSSHAIR_SIZE / viewport.w, a.y, a.z, a.x + CROSSHAIR_SIZE / viewport.w, a.y, a.z);
                    Primitives.addLine(dst, a.x, a.y - CROSSHAIR_SIZE / viewport.h, a.z, a.x, a.y + CROSSHAIR_SIZE / viewport.h, a.z);
                }
            }
        }

        @Override
        public float[] getColor() {
            IView view = getScene().getCurrentView();
            if (view == null)
                return MODEL_COLOR;
            return getContext(view).calibrated ? CALIBRATION_COLOR_CALIBRATED : CALIBRATION_COLOR_UNCALIBRATED;
        }
    };

    public CalibrationTool(IScene scene, ICalibrationModel model) {
        super(scene);
        this.model = model;
    }

    @Override
    public void activate() {
        IRenderer.GROUPS.add(modelLines, modelPoints, calibrationPoints, calibrationLines);
        IRenderer.GROUPS.setSource(Source.TOOL);
    }

    @Override
    public void deactivate() {
        IRenderer.GROUPS.remove(modelLines, modelPoints, calibrationPoints, calibrationLines);
        IRenderer.GROUPS.setSource(null);
        getScene().enableViews(null);
    }

    @Override
    public void refresh(IView view) {
        getScene().enableViews(Collections.singleton(view));
        calibrationPoints.requestUpdate();
        calibrationLines.requestUpdate();
    }

    @Override
    public void keyPressed(KeyEvent e, IView view) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_L:
                loadCalibration(view);
                break;
            case KeyEvent.VK_S:
                saveCalibration(view);
                break;
            case KeyEvent.VK_UP:
                cursorAdjust(view, 0, 1);
                break;
            case KeyEvent.VK_DOWN:
                cursorAdjust(view, 0, -1);
                break;
            case KeyEvent.VK_LEFT:
                cursorAdjust(view, -1, 0);
                break;
            case KeyEvent.VK_RIGHT:
                cursorAdjust(view, 1, 0);
                break;
            case KeyEvent.VK_C:
                clearCalibration(view);
                break;
            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_DELETE:
                deleteCurrent(view);
                break;
    		case KeyEvent.VK_H:
    			AbstractScene.printHelp(HELP);
    			break;
        }
        view.getScene().repaintViews();
    }

    @Override
    public void mousePressed(MouseEvent e, IView view) {
        int mx = e.getX();
        int my = view.getViewport().h - e.getY();
        CalibrationContext context = getContext(view);

        // reset first
        context.currentSelection = -1;

        // first, try to hit calibration point
        for (int i = 0; i < context.projectedVertices.size(); ++i) {
            int x = ProjectionUtil.deviceToScreenX(view, context.projectedVertices.get(i).x);
            int y = ProjectionUtil.deviceToScreenY(view, context.projectedVertices.get(i).y);
            if (snap2D(mx, my, x, y)) {
                // we got a point to move!
                context.currentSelection = i;
                calibrate(view);
                return;
            }
        }

        // second, try to hit model point
        float[] mv = model.getCalibrationVertices();
        for (int i = 0; i < mv.length; i += 3) {
            Vec3 vv = ProjectionUtil.projectToScreen(view, new Vec3(mv[i], mv[i + 1], mv[i + 2]));
            if (vv == null)
                continue;
            if (snap2D(mx, my, (int) vv.x, (int) vv.y)) {
                Vec3 a = new Vec3(mv[i], mv[i + 1], mv[i + 2]);
                int index = context.modelVertices.indexOf(a);
                if (index != -1) {
                    context.currentSelection = index;
                } else {
                    context.currentSelection = context.modelVertices.size();
                    context.modelVertices.add(a);
                    context.projectedVertices.add(new Vec3(ProjectionUtil.screenToDeviceX(view, (int) vv.x), ProjectionUtil.screenToDeviceY(view, (int) vv.y), 0));
                }
                calibrate(view);
                return;
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e, IView view) {
        int mx = e.getX();
        int my = view.getViewport().h - e.getY();
        CalibrationContext context = getContext(view);

        if (context.currentSelection != -1) {
            Vec3 a = new Vec3(ProjectionUtil.screenToDeviceX(view, mx), ProjectionUtil.screenToDeviceY(view, my), 0);
            context.projectedVertices.set(context.currentSelection, a);
            calibrate(view);
        }
    }

    private CalibrationContext getContext(IView view) {
        CalibrationContext context = contexts.get(view);
        if (context == null) {
            context = new CalibrationContext();
            contexts.put(view, context);
        }
        return context;
    }

    private void cursorAdjust(IView view, float dx, float dy) {
        CalibrationContext context = getContext(view);
        if (context.currentSelection != -1) {
            Vec3 p = context.projectedVertices.get(context.currentSelection);
            Vec3 a = new Vec3(p.x + dx / view.getViewport().w, p.y + dy / view.getViewport().h, 0);
            context.projectedVertices.set(context.currentSelection, a);
            calibrate(view);
        }
    }

    private void deleteCurrent(IView view) {
        CalibrationContext context = getContext(view);
        if (context.currentSelection != -1) {
            context.modelVertices.remove(context.currentSelection);
            context.projectedVertices.remove(context.currentSelection);
            context.currentSelection = -1;
            calibrate(view);
        }
    }

    private void loadCalibration(IView view) {
        Preferences p = PreferencesStore.get();
        int iv = 0;
        for (IView v : view.getScene().getViews()) {
            getContext(v).load(p, iv);
            calibrate(v);
            iv++;
        }
    }

    private void saveCalibration(IView view) {
        Preferences p = PreferencesStore.get();
        int iv = 0;
        for (IView v : view.getScene().getViews()) {
            getContext(v).save(p, iv);
            iv++;
        }
    }

    private void clearCalibration(IView view) {
        contexts.put(view, new CalibrationContext());
        view.getCamera().setMatrices(null, null);
        calibrate(view);
    }

    private void calibrate(IView view) {
        CalibrationContext context = getContext(view);
        context.calibrated = false;
        try {
            double error = calibrator.calibrate(context.modelVertices, context.projectedVertices, view.getCamera().getNearClippingPlane(), view.getCamera().getFarClippingPlane());
            if (error < MAX_CALIBRATION_ERROR)
                context.calibrated = true;
            // System.out.println("error: " + error);
        } catch (Throwable ignored) {
        }
        if (context.calibrated)
            view.getCamera().setMatrices(calibrator.getProjMatrix(), calibrator.getViewMatrix());
        else
            view.getCamera().setMatrices(null, null);

        // need to update VBOs
        calibrationPoints.requestUpdate();
        calibrationLines.requestUpdate();

        // lazily repaint all views
        view.getScene().repaintViews();
    }
}
