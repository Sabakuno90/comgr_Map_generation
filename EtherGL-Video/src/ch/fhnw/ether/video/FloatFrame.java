package ch.fhnw.ether.video;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class FloatFrame extends Frame {
	private static final float[] MIN_MAX_0_1    = {0f,1f};
	public float[]               originalMinMax = MIN_MAX_0_1;
	FloatBuffer                  buffer;

	protected FloatFrame() {
		super(4);
		buffer = pixels.asFloatBuffer();
	}

	public FloatFrame(Frame frame) {
		this(frame, MIN_MAX_0_1);
	}
	
	public FloatFrame(FloatFrame frame) {
		this(frame, frame.originalMinMax);
	}

	public FloatFrame(int dimI, int dimJ) {
		this(dimI, dimJ, 1);
	}

	public FloatFrame(int dimI, int dimJ, int dimK) {
		super(4);
		buffer = pixels.asFloatBuffer();
		init(dimI, dimJ, dimK);		
	}

	public FloatFrame(int dimI, int dimJ, int dimK, ByteBuffer frameBuffer) {
		super(dimI, dimJ, dimK, frameBuffer, 4);
		pixels.rewind();
		buffer = pixels.asFloatBuffer();
	}

	public FloatFrame(Frame frame, float[] minMax) {
		this(frame.dimI, frame.dimJ, frame.dimK);
		if(pixelSize == frame.pixelSize && frame instanceof FloatFrame)
			BufferUtilities.arraycopy(frame.pixels, 0, pixels, 0, pixels.capacity());
		else {
			Grey16Codec encoder = new Grey16Codec( minMax );
			final ByteBuffer  src    = frame.pixels;
			int               sps    = frame.pixelSize;
			if(frame instanceof Grey16Frame) {
				for(int k = 0; k < dimK; k++) {
					int spos = k * dimJ * dimI * frame.pixelSize;
					int dpos = k * dimJ * dimI;
					for(int j = 0; j < dimJ; j++) {
						for(int i = 0; i < dimI; i++) {
							buffer.put(dpos++, encoder.decode(src.get(spos+1), src.get(spos)));
							spos += sps;
						}
					}
				}
			} else {
				for(int k = 0; k < dimK; k++) {
					int spos = k * dimJ * dimI * frame.pixelSize;
					int dpos = k * dimJ * dimI;
					for(int j = 0; j < dimJ; j++) {
						for(int i = 0; i < dimI; i++) {
							int val = src.get(spos) & 0xFF;
							val += src.get(spos) & 0xFF;
							val += src.get(spos) & 0xFF;
							val /= 3;
							buffer.put(dpos++, encoder.decode(val, val));
							spos += sps;
						}
					}
				}
			}
		}
		originalMinMax = minMax;
	}


	@Override
	public void init(int dimI, int dimJ, int dimK) {
		super.init(dimI, dimJ, dimK);
		buffer = pixels.asFloatBuffer();
	}

	@Override
	public void setPixels(int x, int y, int w, int h, BufferedImage img, int flags) {
		final int    dstll   = dimI;
		int          dstyoff = dstll * ((dimJ - 1) - y);

		if(img.getType() != BufferedImage.TYPE_USHORT_GRAY) {
			BufferedImage tmp = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_USHORT_GRAY);
			Graphics g = tmp.getGraphics();
			g.drawImage(img, x, y, x + w, y + h, x, y, x + w, y + h, ImageScaler.AWT_OBSERVER);
			g.dispose();
			img = tmp;
		}

		Grey16Codec encoder = new Grey16Codec(new float[] { .0f, 1.0f });

		final short[]     src     = ((DataBufferUShort) img.getRaster().getDataBuffer()).getData();
		final int         srcll   = img.getWidth();
		int               srcyoff = srcll * y + x;
		for(;h > 0; h--) {
			buffer.position(dstyoff + x);
			for(int i = 0; i < w; i++)
				buffer.put(encoder.decode(src[srcyoff + i]));
			srcyoff += srcll;
			dstyoff -= dstll;
		}
	}

	@Override
	public BufferedImage toBufferedImage() {
		BufferedImage result = new BufferedImage(dimI, dimJ, BufferedImage.TYPE_USHORT_GRAY);
		short[]       line   = new short[dimI];

		Grey16Codec encoder = new Grey16Codec(getMinMax());

		int y = 0;
		for(int j = dimJ; --j >= 0; y++) {
			for(int i = 0; i < line.length; i++)
				line[i] = encoder.encode(buffer.get(j * dimI + i));

			result.getRaster().setDataElements(0, y, dimI, 1, line);
		}
		return result;	
	}

	@Override
	public Frame copy() {
		Frame result = new FloatFrame(this);
		return result;
	}

	@Override
	public Frame alloc() {
		return new FloatFrame(dimI, dimJ, dimK);
	}

	@Override
	public float getBrightnessBilinear(double u, double v, int k) {
		return getComponentBilinear(u, v, k, 0);		
	}

	@Override
	public float getFloatComponent(int i, int j, int k, int component) {
		float out = buffer.get((k * dimI * dimJ) + (j * dimI) + i);
		
		if ( component == 3 )
			return Float.isNaN( out ) ? 0 : 1;
		
		return out;
	}

	public float[] getMinMax() {
		float minMax[] = { Float.MAX_VALUE, -Float.MAX_VALUE };
		buffer.clear();
		for (int i = buffer.capacity(); --i >= 0;) {
			float val = buffer.get();
			
			if ( Float.isNaN( val ) )
				continue;
			
			minMax[0] = Math.min(minMax[0], val);
			minMax[1] = Math.max(minMax[1], val);			
		}
		return minMax;
	}
	
	private static class Grey16Codec {
		private static int MAX_USHORT = 0xffff;

		private final float min;
		private final float max;
		private final float range;

		public Grey16Codec(float[] minMax) {
			this.min = minMax[0];
			this.max = minMax[1];
			this.range = minMax[1] - minMax[0];
		}

		public short encode(float value) {
			int ival = Math.round(((value - min) / range) * MAX_USHORT);

			return (short)(ival & 0xffff);
		}

		public float decode(int val) {
			int ival = val & 0xffff;						
			return linearInterpolate(min, max, ival / (float)MAX_USHORT);
		}

		public float decode(int msb, int lsb) {
			return decode(msb << 8 & 0xff00 | lsb & 0xff);
		}
	}

	@Override
	public float getBrightness(int i, int j, int k) {
		return buffer.get((k * dimI * dimJ) + (j * dimI) + i);
	}

	@Override
	public void setARGB(int i, int j, int k, int argb) {
		buffer.put((k * dimI * dimJ) + (j * dimI) + i, ((((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 765f));
	}

	public void setBrightness(int i, int j, int k, float value) {
		buffer.put((k * dimI * dimJ) + (j * dimI) + i, value);
	}

	@Override
	public int getARGB(int i, int j, int k) {
		int rgb = (int)(buffer.get((k * dimI * dimJ) + (j * dimI) + i) * 255f) & 0xFF;
		return rgb << 16 | rgb << 8 | rgb | 0xFF000000;
	}

	@Override
	public void getRGBBilinear(double u, double v, int k, byte[] rgb) {
		rgb[0] = rgb[1] = rgb[2] = (byte) (getComponentBilinear(u, v, k, 0) * 255f);			
	}

	@Override
	public FloatFrame getSubframe(int i, int j, int k, int dimI, int dimJ, int dimK) {
		FloatFrame result = new FloatFrame(dimI, dimJ, dimK);
		getSubframeImpl(i, j, k, result);
		return result;
	}

	@Override
	public void setSubframe(int i, int j, int k, Frame src) {
		if(src.getClass() != getClass())
			src = new FloatFrame(src);
		setSubframeImpl(i, j, k, src);
	}	
}