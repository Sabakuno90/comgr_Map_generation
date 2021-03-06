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

package ch.fhnw.ether.examples.video.fx;

import ch.fhnw.ether.image.Frame;
import ch.fhnw.ether.media.Parameter;
import ch.fhnw.ether.video.IVideoRenderTarget;
import ch.fhnw.ether.video.fx.AbstractVideoFX;
import ch.fhnw.ether.video.fx.IVideoFrameFX;

public class MotionBlur extends AbstractVideoFX implements IVideoFrameFX {
	private static final Parameter DECAY = new Parameter("decay", "Decay", 0.01f, 1f, 1f);

	private float[][] buffer  = new float[1][1];

	protected MotionBlur() {
		super(DECAY);
	}

	@Override
	public void processFrame(final double playOutTime, final IVideoRenderTarget target, final Frame frame) {
		if(buffer[0].length != frame.width *3 || buffer.length != frame.height)
			buffer  = new float[frame.height][frame.width * 3];

		float decay = getVal(DECAY);

		frame.processLines((pixels, j) -> {
			int           idx     = 0;
			final float[] bufferJ = buffer[j];
			for(int i = frame.width; --i >= 0;) {
				frame.position(pixels, i, j);

				float r = toFloat(pixels.get());
				float g = toFloat(pixels.get());
				float b = toFloat(pixels.get());

				bufferJ[idx] = mix(r, bufferJ[idx], decay); idx++;
				bufferJ[idx] = mix(g, bufferJ[idx], decay); idx++;
				bufferJ[idx] = mix(b, bufferJ[idx], decay);

				idx -= 2;

				frame.position(pixels, i, j);					
				pixels.put(toByte(bufferJ[idx++]));
				pixels.put(toByte(bufferJ[idx++]));
				pixels.put(toByte(bufferJ[idx++]));

			}
		});
	}
}
