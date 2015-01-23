package ch.fhnw.ether.examples.video.fx;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jtransforms.fft.FloatFFT_2D;

import ch.fhnw.ether.image.Frame;
import ch.fhnw.ether.media.Parameter;
import ch.fhnw.ether.media.PerTargetState;
import ch.fhnw.ether.video.IVideoRenderTarget;
import ch.fhnw.ether.video.fx.AbstractVideoFX;


public class BandPass extends AbstractVideoFX<BandPass.State> {
	private static final Parameter LOW  = new Parameter("low",  "low cutoff frequency",  0, 1, 0);
	private static final Parameter HIGH = new Parameter("high", "high cutoff frequency", 0, 1, 1);

	protected BandPass() {
		super(LOW, HIGH);
	}

	class State extends PerTargetState<IVideoRenderTarget> {
		int         rows = 16;
		int         cols = 16;
		float[][]   r    = new float[rows][cols*2]; 
		float[][]   g    = new float[rows][cols*2]; 
		float[][]   b    = new float[rows][cols*2]; 
		FloatFFT_2D fft  = new FloatFFT_2D(rows, cols);

		public State(IVideoRenderTarget target) {
			super(target);
		}

		void processFrame(double playOutTime, Frame frame) {
			if(rows != frame.dimJ || cols != frame.dimI) {
				rows = frame.dimJ;
				cols = frame.dimI;
				r    = new float[rows][cols*2]; 
				g    = new float[rows][cols*2]; 
				b    = new float[rows][cols*2]; 
				fft  = new FloatFFT_2D(rows, cols);
			}

			frame.processLines((ByteBuffer pixels, int j)->{
				final float[] rj = r[j];
				final float[] gj = g[j];
				final float[] bj = b[j];
				for(int i = frame.dimI; --i >= 0;) {
					rj[i*2+0] = toFloat(pixels.get()); 
					rj[i*2+1] = 0f;
					gj[i*2+0] = toFloat(pixels.get()); 
					gj[i*2+1] = 0f;
					bj[i*2+0] = toFloat(pixels.get()); 
					bj[i*2+1] = 0f;
					if(frame.pixelSize == 4) pixels.get();
				}
			});

			int low  = (int)(getVal(LOW)  * (cols - 1));
			int high = (int)(getVal(HIGH) * (cols - 1));

			fft.complexForward(r);
			fft.complexForward(g);
			fft.complexForward(b);
			for(int j = r.length; --j >=0;) {
				Arrays.fill(r[j], 0,        low * 2,     0f);
				Arrays.fill(r[j], high * 2, r[j].length, 0f);

				Arrays.fill(g[j], 0,        low * 2,     0f);
				Arrays.fill(g[j], high * 2, g[j].length, 0f);

				Arrays.fill(b[j], 0,        low * 2,     0f);
				Arrays.fill(b[j], high * 2, b[j].length, 0f);
			}
			fft.complexInverse(r, true);
			fft.complexInverse(g, true);
			fft.complexInverse(b, true);

			frame.processLines((ByteBuffer pixels, int j)->{
				final float[] rj = r[j];
				final float[] gj = g[j];
				final float[] bj = b[j];
				for(int i = frame.dimI; --i >= 0;) {
					float re = rj[i*2+0];
					float im = rj[i*2+1];
					pixels.put(toByte(Math.sqrt(re * re + im * im)));
					re = gj[i*2+0];
					im = gj[i*2+1];
					pixels.put(toByte(Math.sqrt(re * re + im * im)));
					re = bj[i*2+0];
					im = bj[i*2+1];
					pixels.put(toByte(Math.sqrt(re * re + im * im)));
					if(frame.pixelSize == 4) pixels.get();
				}
			});
		}
	}

	@Override
	protected State createState(IVideoRenderTarget target) {
		return new State(target);
	}
	
	@Override
	protected void processFrame(double playOutTime, State state, Frame frame) {
		state.processFrame(playOutTime, frame);
	}
}