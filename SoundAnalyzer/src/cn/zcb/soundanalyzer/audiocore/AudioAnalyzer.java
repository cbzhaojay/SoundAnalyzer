package cn.zcb.soundanalyzer.audiocore;

import org.hermit.dsp.FFTTransformer;
import org.hermit.dsp.Window;

import android.view.SurfaceView;

public class AudioAnalyzer extends Instrument {
	public AudioAnalyzer(SurfaceView parent) {
		super(parent);
		parentSurface = parent;

		audioReader = new AudioReader();

		spectrumAnalyser = new FFTTransformer(inputBlockSize, windowFunction);

		// Allocate the spectrum data.
		spectrumData = new float[inputBlockSize / 2];
		spectrumHist = new float[inputBlockSize / 2][historyLen];
		spectrumIndex = 0;

		biasRange = new float[2];
	}

	/**
	 * Set the sample rate for this instrument.
	 * 
	 * @param rate
	 *            The desired rate, in samples/sec.
	 */
	public void setSampleRate(int rate) {
		sampleRate = rate;
	}
	   /**
     * Set the input block size for this instrument.
     * 
     * @param   size        The desired block size, in samples.  Typical
     *                      values would be 256, 512, or 1024.  Larger block
     *                      sizes will mean more work to analyse the spectrum.
     */
    public void setBlockSize(int size) {
        inputBlockSize = size;

        spectrumAnalyser = new FFTTransformer(inputBlockSize, windowFunction);

        // Allocate the spectrum data.
        spectrumData = new float[inputBlockSize / 2];
        spectrumHist = new float[inputBlockSize / 2][historyLen];
    }
    /**
     * Set the decimation rate for this instrument.
     * 
     * @param   rate        The desired decimation.  Only 1 in rate blocks
     *                      will actually be processed.
     */
    public void setDecimation(int rate) {
        sampleDecimate = rate;
    }

    /**
     * Set the histogram averaging window for this instrument.
     * 
     * @param   len         The averaging interval.  1 means no averaging.
     */
    public void setAverageLen(int len) {
        historyLen = len;
        
        // Set up the history buffer.
        spectrumHist = new float[inputBlockSize / 2][historyLen];
        spectrumIndex = 0;
    }
    /**
     * The application is starting.  Perform any initial set-up prior to
     * starting the application.  We may not have a screen size yet,
     * so this is not a good place to allocate resources which depend on
     * that.
     */
    @Override
    public void appStart() {
    }


    /**
     * We are starting the main run; start measurements.
     */
    @Override
    public void measureStart() {
        audioProcessed = audioSequence = 0;
        readError = AudioReader.Listener.ERR_OK;
        
        audioReader.startReader(sampleRate, inputBlockSize * sampleDecimate, new AudioReader.Listener() {
            @Override
            public final void onReadComplete(short[] buffer) {
                receiveAudio(buffer);
            }
            @Override
            public void onReadError(int error) {
                handleError(error);
            }
        });
    }
    // ******************************************************************** //
    // Audio Processing.
    // ******************************************************************** //

    /**
     * Handle audio input.  This is called on the thread of the audio
     * reader.
     * 
     * @param   buffer      Audio data that was just read.
     */
    private final void receiveAudio(short[] buffer) {
        // Lock to protect updates to these local variables.  See run().
        synchronized (this) {
            audioData = buffer;
            ++audioSequence;
        }
    }
    
    
    /**
     * An error has occurred.  The reader has been terminated.
     * 
     * @param   error       ERR_XXX code describing the error.
     */
    private void handleError(int error) {
        synchronized (this) {
            readError = error;
        }
    }
    /**
     * Update the state of the instrument for the current frame.
     * This method must be invoked from the doUpdate() method of the
     * application's {@link SurfaceRunner}.
     * 
     * <p>Since this is called frequently, we first check whether new
     * audio data has actually arrived.
     * 
     * @param   now         Nominal time of the current frame in ms.
     */
    @Override
    public final void doUpdate(long now) {
        short[] buffer = null;
        synchronized (this) {
            if (audioData != null && audioSequence > audioProcessed) {
               // parentSurface.statsCount(1, (int) (audioSequence - audioProcessed - 1));
                audioProcessed = audioSequence;
                buffer = audioData;
            }
        }

        // If we got data, process it without the lock.
        if (buffer != null)
            processAudio(buffer);
        
        if (readError != AudioReader.Listener.ERR_OK)
            processError(readError);
    }

    /**
     * Handle an audio input error.
     * 
     * @param   error       ERR_XXX code describing the error.
     */
    private final void processError(int error) {
        // Pass the error to all the gauges we have.
       /* if (waveformGauge != null)
            waveformGauge.error(error);
        if (spectrumGauge != null)
            spectrumGauge.error(error);
        if (sonagramGauge != null)
            sonagramGauge.error(error);
        if (powerGauge != null)
            powerGauge.error(error);*/
    }
    
    /**
     * Handle audio input.  This is called on the thread of the
     * parent surface.
     * 
     * @param   buffer      Audio data that was just read.
     */
    private final void processAudio(short[] buffer) {
        // Process the buffer.  While reading it, it needs to be locked.
        synchronized (buffer) {
            // Calculate the power now, while we have the input
            // buffer; this is pretty cheap.
            final int len = buffer.length;}
            /* 
            // Draw the waveform now, while we have the raw data.
            if (waveformGauge != null) {
                SignalPower.biasAndRange(buffer, len - inputBlockSize, inputBlockSize, biasRange);
                final float bias = biasRange[0];
                float range = biasRange[1];
                if (range < 1f)
                    range = 1f;
                
//                long wavStart = System.currentTimeMillis();
                waveformGauge.update(buffer, len - inputBlockSize, inputBlockSize, bias, range);
//                long wavEnd = System.currentTimeMillis();
//                parentSurface.statsTime(1, (wavEnd - wavStart) * 1000);
            }
            
            // If we have a power gauge, calculate the signal power.
            if (powerGauge != null)
                currentPower = SignalPower.calculatePowerDb(buffer, 0, len);

            // If we have a spectrum or sonagram analyser, set up the FFT input data.
            if (spectrumGauge != null || sonagramGauge != null)
                spectrumAnalyser.setInput(buffer, len - inputBlockSize, inputBlockSize);

            // Tell the reader we're done with the buffer.
            buffer.notify();
        }

        // If we have a spectrum or sonagram analyser, perform the FFT.
        if (spectrumGauge != null || sonagramGauge != null) {
            // Do the (expensive) transformation.
            // The transformer has its own state, no need to lock here.
            long specStart = System.currentTimeMillis();
            spectrumAnalyser.transform();
            long specEnd = System.currentTimeMillis();
            parentSurface.statsTime(0, (specEnd - specStart) * 1000);

            // Get the FFT output.
            if (historyLen <= 1)
                spectrumAnalyser.getResults(spectrumData);
            else
                spectrumIndex = spectrumAnalyser.getResults(spectrumData,
                                                            spectrumHist,
                                                            spectrumIndex);
        }
      
        // If we have a spectrum gauge, update data and draw.
        if (spectrumGauge != null)
            spectrumGauge.update(spectrumData);
        	
        // If we have a sonagram gauge, update data and draw.
        if (sonagramGauge != null)
            sonagramGauge.update(spectrumData);

        // If we have a power gauge, display the signal power.
        if (powerGauge != null)
            powerGauge.update(currentPower);*/
    }
    

    /**
     * We are stopping / pausing the run; stop measurements.
     */
    @Override
    public void measureStop() {
        audioReader.stopReader();
    }
    

    /**
     * The application is closing down.  Clean up any resources.
     */
    @Override
    public void appStop() {
    }
    
	@SuppressWarnings("unused")
	private static final String TAG = "instrument";

	// ******************************************************************** //
	// Private Data.
	// ******************************************************************** //

	// Our parent surface.
	private SurfaceView parentSurface;

	// The desired sampling rate for this analyser, in samples/sec.
	private int sampleRate = 8000;

	// Audio input block size, in samples.
	private int inputBlockSize = 256;

	// The selected windowing function.
	private Window.Function windowFunction = Window.Function.BLACKMAN_HARRIS;

	// The desired decimation rate for this analyser. Only 1 in
	// sampleDecimate blocks will actually be processed.
	private int sampleDecimate = 1;

	// The desired histogram averaging window. 1 means no averaging.
	private int historyLen = 4;

	// Our audio input device.
	private final AudioReader audioReader;

	// Fourier Transform calculator we use for calculating the spectrum
	// and sonagram.
	private FFTTransformer spectrumAnalyser;

	// The gauges associated with this instrument. Any may be null if not
	// in use.
	// private WaveformGauge waveformGauge = null;
	// private SpectrumGauge spectrumGauge = null;
	// private SonagramGauge sonagramGauge = null;
	// private PowerGauge powerGauge = null;

	// Buffered audio data, and sequence number of the latest block.
	private short[] audioData;
	private long audioSequence = 0;

	// If we got a read error, the error code.
	private int readError = AudioReader.Listener.ERR_OK;

	// Sequence number of the last block we processed.
	private long audioProcessed = 0;

	// Analysed audio spectrum data; history data for each frequency
	// in the spectrum; index into the history data; and buffer for
	// peak frequencies.
	private float[] spectrumData;
	private float[][] spectrumHist;
	private int spectrumIndex;

	// Current signal power level, in dB relative to max. input power.
	private double currentPower = 0f;

	// Temp. buffer for calculated bias and range.
	private float[] biasRange = null;

}
