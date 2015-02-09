package ch.fhnw.ether.audio;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.jtransforms.fft.FloatFFT_1D;

import ch.fhnw.ether.audio.AudioUtilities.Window;
import ch.fhnw.ether.media.AbstractRenderCommand;
import ch.fhnw.ether.media.PerTargetState;
import ch.fhnw.ether.media.RenderCommandException;
import ch.fhnw.util.IModifier;
import ch.fhnw.util.math.MathUtilities;

public class FFT extends AbstractRenderCommand<IAudioRenderTarget,FFT.State> {
	private final float  minFreq;
	private final Window windowType;

	public class State extends PerTargetState<IAudioRenderTarget> {
		private final FloatFFT_1D   fft;
		private final BlockBuffer   buffer;
		private final int           fftSize;
		private final List<float[]> spectrum = new LinkedList<>();
		private       float[]       block;
		private final float         sRate;
		private final float[]       power;
		private       float[]       pcm0;
		private       int           pcm0rd;
		private       float[]       pcm1;
		private       int           pcm1rd;

		State(IAudioRenderTarget target) {
			super(target);
			sRate   = target.getSampleRate();
			fftSize = MathUtilities.nextPowerOfTwo((int)(sRate / minFreq));
			System.out.println("Using FFT of " + fftSize + " at " + sRate + " Hz");
			fft      = new FloatFFT_1D(fftSize);
			buffer   = new BlockBuffer(fftSize, true, windowType);
			block    = new float[fftSize];
			power    = new float[fftSize / 2];
			pcm1     = new float[fftSize];
			pcm1rd   = fftSize / 2;
			pcm0rd   = fftSize;
		}

		void process(AudioFrame frame) {
			buffer.add(frame.getMonoSamples());
			int nBlocks = 0;
			for(block = buffer.nextBlock(); block  != null; block = buffer.nextBlock()) {
				if(nBlocks == 0)
					Arrays.fill(power, 0f);

				fft.realForward(block);
				spectrum.add(block);
				final int lim = block.length / 2;
				for(int i = 0; i < lim; i+= 2) {
					final float  re = block[i+0];
					final float  im = block[i+1];
					final double p  = Math.sqrt(re * re + im * im);
					power[i >> 1] += (float)p;
				}
				nBlocks++;
			}

			if(nBlocks > 0) {
				float div = nBlocks;
				for(int i = 0; i < power.length; i++)
					power[i] /= div;
			}
		}

		float power(float fLow, float fHigh) {
			int iLow  = f2idx(fLow);
			int iHigh = f2idx(fHigh);
			if(iHigh <= iLow) iHigh = iLow + 1;
			if(iHigh >= fftSize) iHigh = fftSize;
			if(iLow  >= iHigh) iLow = iHigh - 1;
			double result = 0;
			for(int i = iLow; i < iHigh; i++)
				result += power[i];
						
			return (float) result;
		}

		float[] power() {
			return power;
		}

		public int f2idx(float f) {
			int result = (int) ((fftSize * f) / sRate);
			if(result < 0)        return 0;
			if(result >= fftSize) return fftSize - 1;
			return result;
		}

		public float idx2f(int idx) {
			return (idx * sRate) / fftSize;
		}

		public int size() {
			return fftSize;
		}

		public void inverse() {
			final AudioFrame frame   = getTarget().getFrame(); 
			final float[]    samples = frame.samples;

			if(spectrum.size() < Math.max(1, 2 * (frame.samples.length / frame.nChannels) / fftSize)) {
				Arrays.fill(samples, 0f);
				return;
			}

			final int nChannels = frame.nChannels;
			for(int i = 0; i < samples.length; i += nChannels) {
				if(pcm0rd >= fftSize) {
					pcm0  = spectrum.remove(0);
					fft.realInverse(pcm0, true);
					pcm0rd = 0;
				}
				if(pcm1rd >= fftSize) {
					pcm1  = spectrum.remove(0);
					fft.realInverse(pcm1, true);
					pcm1rd = 0;
				}
				float sample = (pcm0[pcm0rd++] + pcm1[pcm1rd++]) / 2;
				for(int c = 0; c < nChannels; c++)
					samples[i+c] = sample;
			}
		}
	}

	public FFT(float minFreq, Window windowType) {
		this.minFreq    = minFreq;
		this.windowType = windowType;
	}

	@Override
	protected void run(State state) throws RenderCommandException {
		state.process(state.getTarget().getFrame());
	}	

	@Override
	protected State createState(IAudioRenderTarget target) throws RenderCommandException {
		return new State(target);
	}

	public float power(IAudioRenderTarget target, float fLow, float fHigh) {
		return getState(target).power(fLow, fHigh);
	}

	public float[] power(IAudioRenderTarget target) {
		return getState(target).power();
	}

	public int size(IAudioRenderTarget target) {
		return getState(target).size();
	}

	public int f2idx(IAudioRenderTarget target, float f) {
		return getState(target).f2idx(f);
	}

	public float idx2f(IAudioRenderTarget target, int idx) {
		return getState(target).idx2f(idx);
	}

	public float[] inverse(IAudioRenderTarget target, float[] spectrum) {
		getState(target).fft.complexInverse(spectrum, true);
		return spectrum;
	}

	public void inverse(IAudioRenderTarget target) {
		getState(target).inverse();
	}

	public void modifySpectrum(IAudioRenderTarget target, IModifier<float[]> modifier) {
		for(float[] spectrum : getState(target).spectrum)
			modifier.modify(spectrum);
	}
}