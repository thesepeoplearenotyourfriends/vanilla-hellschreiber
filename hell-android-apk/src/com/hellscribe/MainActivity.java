package com.hellscribe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements View.OnClickListener {
    private static final int ROWS = 7;
    private static final int SAMPLE_RATE = 8000;
    private static final double AMPLITUDE = 0.65;
    private static final int CHAR_SPACING = 1;
    private static final int WORD_SPACING = 3;
    private static final int MIC_PERMISSION_REQUEST = 41;
    private static final int SOURCE_AUTO = -1;
    private static final int TEST_CARRIER = 1;
    private static final int TEST_STRIPES = 2;
    private static final int TEST_BARS = 3;
    private static final int TEST_REPEAT = 4;
    private static final int TEST_CQ = 5;
    private static final Map<Character, String[]> FONT = buildFont();

    private int bg;
    private int panel;
    private int ink;
    private int muted;
    private int line;
    private int accent;
    private int field;
    private int accentText;

    private EditText messageInput;
    private EditText toneInput;
    private EditText dotRateInput;
    private EditText thresholdInput;
    private EditText contrastInput;
    private EditText scaleInput;
    private TextView status;
    private TextView asciiPreview;
    private HellStripView stripView;
    private Button renderButton;
    private Button playButton;
    private Button listenButton;
    private Button stopButton;
    private Button sourceButton;
    private Button calibrateButton;
    private Button preambleButton;
    private Button normalButton;
    private Button roomButton;
    private Button slowButton;
    private Button verySlowButton;
    private Button carrierButton;
    private Button stripesButton;
    private Button barsButton;
    private Button repeatButton;
    private Button cqButton;

    private volatile boolean playing;
    private volatile boolean listening;
    private Thread playbackThread;
    private Thread receiveThread;
    private AudioTrack audioTrack;
    private AudioRecord audioRecord;
    private int requestedAudioSource = SOURCE_AUTO;
    private boolean txPreamble = true;
    private boolean unprocessedReportedSupported;
    private volatile double calibratedNoiseFloor = -1.0;
    private volatile String lastRxDebug = "RX idle.";
    private boolean pendingCalibration;
    private double pendingCalibrationTone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configurePalette();
        unprocessedReportedSupported = isUnprocessedReportedSupported();
        requestedAudioSource = chooseDefaultAudioSource();
        buildUi();
        renderMessage();
    }

    @Override
    protected void onStop() {
        stopPlayback();
        stopListening();
        super.onStop();
    }

    private void configurePalette() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            bg = Color.rgb(18, 18, 18);
            panel = Color.rgb(31, 28, 24);
            ink = Color.rgb(246, 232, 210);
            muted = Color.rgb(189, 174, 151);
            line = Color.rgb(82, 72, 60);
            accent = Color.rgb(255, 180, 95);
            field = Color.rgb(12, 12, 12);
            accentText = Color.rgb(32, 22, 10);
        } else {
            bg = Color.rgb(247, 244, 237);
            panel = Color.rgb(255, 250, 240);
            ink = Color.rgb(36, 33, 28);
            muted = Color.rgb(107, 98, 86);
            line = Color.rgb(217, 208, 194);
            accent = Color.rgb(138, 75, 8);
            field = Color.WHITE;
            accentText = Color.WHITE;
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        TextView title = text("HellScribe", 30, ink);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView intro = text("A homebrew Feld-Hell / Hellschreiber toy for touch, speaker playback, and microphone receive. No files required.", 15, muted);
        intro.setPadding(0, dp(2), 0, dp(10));
        root.addView(intro);

        LinearLayout messagePanel = panel();
        root.addView(messagePanel);
        messagePanel.addView(label("Message text"));
        messageInput = new EditText(this);
        messageInput.setText("CQ CQ HELLSCHREIBER 123");
        messageInput.setMinLines(4);
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        messageInput.setSingleLine(false);
        messageInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(280)});
        styleField(messageInput);
        messagePanel.addView(messageInput, matchWrap());

        LinearLayout buttonRow1 = row();
        LinearLayout buttonRow2 = row();
        messagePanel.addView(buttonRow1);
        messagePanel.addView(buttonRow2);
        renderButton = primaryButton("Render");
        playButton = primaryButton("Play speaker");
        listenButton = secondaryButton("Listen mic");
        stopButton = secondaryButton("Stop");
        buttonRow1.addView(renderButton, weightedButton());
        buttonRow1.addView(playButton, weightedButton());
        buttonRow2.addView(listenButton, weightedButton());
        buttonRow2.addView(stopButton, weightedButton());

        renderButton.setOnClickListener(this);
        playButton.setOnClickListener(this);
        listenButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);

        LinearLayout controls = panel();
        root.addView(controls);
        controls.addView(label("Controls"));
        LinearLayout row1 = row();
        LinearLayout row2 = row();
        LinearLayout row3 = row();
        LinearLayout row4 = row();
        LinearLayout row5 = row();
        LinearLayout row6 = row();
        controls.addView(row1);
        controls.addView(row2);
        controls.addView(row3);
        controls.addView(row4);
        controls.addView(row5);
        controls.addView(row6);
        toneInput = control(row1, "Tone Hz", "1000");
        dotRateInput = control(row1, "Dot rate", "122.5");
        scaleInput = control(row1, "Scale", "8");
        thresholdInput = control(row2, "RX threshold", "0.45");
        contrastInput = control(row2, "RX contrast", "3.0");
        sourceButton = secondaryButton("RX source: " + audioSourceName(requestedAudioSource));
        calibrateButton = secondaryButton("Calibrate noise");
        preambleButton = secondaryButton("TX preamble: on");
        row3.addView(sourceButton, weightedButton());
        row3.addView(calibrateButton, weightedButton());
        row3.addView(preambleButton, weightedButton());
        normalButton = secondaryButton("Normal");
        roomButton = secondaryButton("Room");
        slowButton = secondaryButton("Slow");
        verySlowButton = secondaryButton("Very Slow");
        row4.addView(normalButton, weightedButton());
        row4.addView(roomButton, weightedButton());
        row4.addView(slowButton, weightedButton());
        row4.addView(verySlowButton, weightedButton());
        carrierButton = secondaryButton("Carrier 5s");
        stripesButton = secondaryButton("Stripes");
        barsButton = secondaryButton("Sync bars");
        row5.addView(carrierButton, weightedButton());
        row5.addView(stripesButton, weightedButton());
        row5.addView(barsButton, weightedButton());
        repeatButton = secondaryButton("HELL repeat");
        cqButton = secondaryButton("CQ test");
        row6.addView(repeatButton, weightedButton());
        row6.addView(cqButton, weightedButton());

        sourceButton.setOnClickListener(this);
        calibrateButton.setOnClickListener(this);
        preambleButton.setOnClickListener(this);
        normalButton.setOnClickListener(this);
        roomButton.setOnClickListener(this);
        slowButton.setOnClickListener(this);
        verySlowButton.setOnClickListener(this);
        carrierButton.setOnClickListener(this);
        stripesButton.setOnClickListener(this);
        barsButton.setOnClickListener(this);
        repeatButton.setOnClickListener(this);
        cqButton.setOnClickListener(this);

        LinearLayout stripPanel = panel();
        root.addView(stripPanel);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.setBackgroundColor(field);
        stripView = new HellStripView(this);
        stripView.setPalette(field, ink, accent, line);
        horizontal.addView(stripView);
        stripPanel.addView(horizontal, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)));
        status = text("", 14, muted);
        status.setPadding(0, dp(8), 0, 0);
        stripPanel.addView(status);

        LinearLayout asciiPanel = panel();
        root.addView(asciiPanel);
        asciiPanel.addView(label("ASCII preview"));
        asciiPreview = text("", 13, ink);
        asciiPreview.setTypeface(android.graphics.Typeface.MONOSPACE);
        asciiPreview.setBackgroundColor(field);
        asciiPreview.setPadding(dp(10), dp(10), dp(10), dp(10));
        asciiPanel.addView(asciiPreview, matchWrap());

        setContentView(scroll);
    }

    @Override
    public void onClick(View view) {
        if (view == renderButton) {
            renderMessage();
        } else if (view == playButton) {
            startPlayback();
        } else if (view == listenButton) {
            toggleListening();
        } else if (view == stopButton) {
            stopPlayback();
            stopListening();
            setStatus("Stopped.");
        } else if (view == sourceButton) {
            cycleAudioSource();
        } else if (view == calibrateButton) {
            startCalibration();
        } else if (view == preambleButton) {
            txPreamble = !txPreamble;
            preambleButton.setText(txPreamble ? "TX preamble: on" : "TX preamble: off");
        } else if (view == normalButton) {
            setDotRatePreset("122.5", "Normal Feld-Hell speed.");
        } else if (view == roomButton) {
            setDotRatePreset("80", "Room mode: slower acoustic dots.");
        } else if (view == slowButton) {
            setDotRatePreset("50", "Slow mode: more samples per Hell pixel.");
        } else if (view == verySlowButton) {
            setDotRatePreset("30", "Very Slow mode for difficult speaker/mic paths.");
        } else if (view == carrierButton) {
            startTestPlayback(TEST_CARRIER);
        } else if (view == stripesButton) {
            startTestPlayback(TEST_STRIPES);
        } else if (view == barsButton) {
            startTestPlayback(TEST_BARS);
        } else if (view == repeatButton) {
            startTestPlayback(TEST_REPEAT);
        } else if (view == cqButton) {
            startTestPlayback(TEST_CQ);
        }
    }

    private void cycleAudioSource() {
        if (requestedAudioSource == MediaRecorder.AudioSource.UNPROCESSED) {
            requestedAudioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
        } else if (requestedAudioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION) {
            requestedAudioSource = MediaRecorder.AudioSource.MIC;
        } else if (requestedAudioSource == MediaRecorder.AudioSource.MIC && Build.VERSION.SDK_INT >= 29) {
            requestedAudioSource = MediaRecorder.AudioSource.VOICE_PERFORMANCE;
        } else {
            requestedAudioSource = MediaRecorder.AudioSource.UNPROCESSED;
        }
        sourceButton.setText("RX source: " + audioSourceName(requestedAudioSource));
        setStatus("RX source requested: " + audioSourceName(requestedAudioSource)
                + ". UNPROCESSED reported supported: " + yesNo(unprocessedReportedSupported) + ".");
    }

    private void setDotRatePreset(String value, String message) {
        dotRateInput.setText(value);
        setStatus(message);
        renderMessage();
    }

    private void startTestPlayback(int testMode) {
        stopListening();
        stopPlayback();
        final double toneHz = Math.max(50.0, readDouble(toneInput, 1000.0));
        final double dotRate = Math.max(1.0, readDouble(dotRateInput, 122.5));
        ArrayList<boolean[]> columns = testColumns(testMode, dotRate);
        stripView.setScale(readInt(scaleInput, 8));
        stripView.setThreshold(readDouble(thresholdInput, 0.45));
        stripView.setColumns(columns);
        asciiPreview.setText(testMode == TEST_CARRIER ? "Solid carrier for 5 seconds." : asciiFromColumns(columns));
        playing = true;
        playButton.setText("Stop play");
        playbackThread = new PlaybackThread(this, columns, toneHz, dotRate, testMode == TEST_CARRIER);
        playbackThread.start();
    }

    private void renderMessage() {
        ArrayList<boolean[]> columns = textToColumns(messageInput.getText().toString());
        stripView.setScale(readInt(scaleInput, 8));
        stripView.setThreshold(readDouble(thresholdInput, 0.45));
        stripView.setColumns(columns);
        asciiPreview.setText(asciiFromColumns(columns));
        setStatus(String.format(Locale.US, "Rendered %d Hell columns at %.1f dots/s.", columns.size(), readDouble(dotRateInput, 122.5)));
    }

    private void startPlayback() {
        if (playing) {
            stopPlayback();
            return;
        }
        stopListening();
        final double toneHz = Math.max(50.0, readDouble(toneInput, 1000.0));
        final double dotRate = Math.max(1.0, readDouble(dotRateInput, 122.5));
        final ArrayList<boolean[]> columns = txPreamble ? addPreamble(textToColumns(messageInput.getText().toString()), dotRate)
                : textToColumns(messageInput.getText().toString());
        playing = true;
        playButton.setText("Stop play");
        playbackThread = new PlaybackThread(this, columns, toneHz, dotRate, false);
        playbackThread.start();
    }

    private void playColumns(ArrayList<boolean[]> columns, double toneHz, double dotRate, boolean solidCarrier) {
        int dotSamples = Math.max(8, (int) Math.round(SAMPLE_RATE / dotRate));
        int totalSamples = solidCarrier ? SAMPLE_RATE * 5 : Math.max(1, columns.size() * ROWS * dotSamples);
        short[] pcm = new short[totalSamples];
        double phase = 0.0;
        double step = 2.0 * Math.PI * toneHz / SAMPLE_RATE;
        int index = 0;
        if (solidCarrier) {
            while (index < pcm.length) {
                double value = Math.sin(phase) * AMPLITUDE;
                pcm[index++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value * Short.MAX_VALUE));
                phase += step;
                if (phase > Math.PI * 2.0) phase -= Math.PI * 2.0;
            }
        } else {
            for (int c = 0; c < columns.size(); c++) {
                boolean[] column = columns.get(c);
                for (int row = 0; row < ROWS; row++) {
                    boolean keyed = column[row];
                    for (int i = 0; i < dotSamples; i++) {
                        double envelope = 1.0;
                        int ramp = Math.max(1, dotSamples / 8);
                        if (i < ramp) envelope = i / (double) ramp;
                        if (i > dotSamples - ramp) envelope = Math.min(envelope, (dotSamples - i) / (double) ramp);
                        double value = keyed ? Math.sin(phase) * AMPLITUDE * envelope : 0.0;
                        pcm[index++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value * Short.MAX_VALUE));
                        phase += step;
                        if (phase > Math.PI * 2.0) phase -= Math.PI * 2.0;
                    }
                }
            }
        }
        String finalStatus = "Playback finished.";
        try {
            int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int streamBufferBytes = Math.max(minBuffer, dotSamples * ROWS * 2);
            AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, streamBufferBytes, AudioTrack.MODE_STREAM);
            audioTrack = track;
            track.play();
            setStatus("Playing Hell tone over the speaker.");
            int written = 0;
            int chunkSamples = Math.max(1, streamBufferBytes / 2);
            while (playing && written < pcm.length) {
                int result = track.write(pcm, written, Math.min(chunkSamples, pcm.length - written));
                if (result <= 0) break;
                written += result;
            }
            while (playing && audioTrack == track && track.getPlaybackHeadPosition() < written) {
                Thread.sleep(20);
            }
            if (!playing || written < pcm.length) {
                finalStatus = "Playback stopped.";
            }
        } catch (InterruptedException ex) {
            finalStatus = "Playback stopped.";
            Thread.currentThread().interrupt();
        } catch (IllegalStateException ex) {
            finalStatus = playing ? "Speaker playback could not start on this device." : "Playback stopped.";
        } finally {
            releaseTrack();
            runOnUiThread(new PlaybackFinishedUpdate(this, finalStatus));
        }
    }

    private void toggleListening() {
        if (listening) {
            stopListening();
            setStatus("Microphone receive stopped.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST);
            return;
        }
        startListening();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingCalibration) {
                pendingCalibration = false;
                calibrateButton.setText("Calibrating...");
                new CalibrationThread(this, pendingCalibrationTone).start();
            } else {
                startListening();
            }
        } else {
            pendingCalibration = false;
            setStatus("Microphone permission is needed for receive.");
        }
    }

    private void startListening() {
        stopPlayback();
        stripView.setScale(readInt(scaleInput, 8));
        stripView.setThreshold(readDouble(thresholdInput, 0.45));
        stripView.clearEnergy();
        asciiPreview.setText("Microphone receive is visual-only; read the strip above.");
        final double toneHz = Math.max(50.0, readDouble(toneInput, 1000.0));
        final double dotRate = Math.max(1.0, readDouble(dotRateInput, 122.5));
        final double contrast = Math.max(0.1, readDouble(contrastInput, 3.0));
        listening = true;
        listenButton.setText("Stop mic");
        receiveThread = new ReceiveThread(this, toneHz, dotRate, contrast);
        receiveThread.start();
    }

    private void receiveMicrophone(double toneHz, double dotRate, double contrast) {
        int dotSamples = Math.max(8, (int) Math.round(SAMPLE_RATE / dotRate));
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, dotSamples * 8);
        short[] buffer = new short[bufferSize / 2];
        short[] dot = new short[dotSamples];
        int dotFill = 0;
        int row = 0;
        float[] column = new float[ROWS];
        double floor = calibratedNoiseFloor >= 0.0 ? calibratedNoiseFloor : 0.02;
        double peak = Math.max(floor + 0.08, floor * 4.0);
        long lastDebugMs = 0L;
        String effectStatus = "effects pending";
        RecordStart start = null;
        try {
            start = createAudioRecord(requestedAudioSource, bufferSize);
            audioRecord = start.record;
            effectStatus = attachAndDisableEffects(audioRecord.getAudioSessionId());
            audioRecord.startRecording();
            setStatus(rxDebugLine(start.source, effectStatus, bufferSize, 0.0, 0.0, floor, peak));
            while (listening) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read <= 0) continue;
                for (int i = 0; i < read && listening; i++) {
                    dot[dotFill++] = buffer[i];
                    if (dotFill == dotSamples) {
                        ToneMetrics metrics = detectTone(dot, dotSamples, toneHz);
                        if (metrics.tone < floor) floor = floor * 0.98 + metrics.tone * 0.02;
                        else floor = floor * 0.995 + metrics.tone * 0.005;
                        if (metrics.tone > peak) peak = peak * 0.85 + metrics.tone * 0.15;
                        else peak = peak * 0.995 + metrics.tone * 0.005;
                        if (peak < floor + 0.02) peak = floor + 0.02;
                        double value = (metrics.tone - floor) / (peak - floor);
                        value = Math.max(0.0, Math.min(1.0, value * contrast));
                        column[row] = (float) value;
                        long now = System.currentTimeMillis();
                        if (now - lastDebugMs > 350L) {
                            lastDebugMs = now;
                            setStatus(rxDebugLine(start.source, effectStatus, bufferSize, metrics.rms, metrics.tone, floor, peak));
                        }
                        row++;
                        if (row == ROWS) {
                            stripView.appendEnergy((float[]) column.clone());
                            row = 0;
                            column = new float[ROWS];
                        }
                        dotFill = 0;
                    }
                }
            }
        } catch (SecurityException ex) {
            setStatus("Microphone permission was denied.");
        } catch (IllegalStateException ex) {
            setStatus("Microphone receive could not start on this device. " + ex.getMessage());
        } finally {
            releaseRecord();
            runOnUiThread(new ReceiveFinishedUpdate(this));
        }
    }

    private String rxDebugLine(int source, String effects, int bufferSize, double rms, double tone, double floor, double peak) {
        lastRxDebug = String.format(Locale.US,
                "RX %s | UNPROCESSED supported %s | %s | %d Hz | buf %d | RMS %.3f | tone %.3f | floor %.3f peak %.3f",
                audioSourceName(source), yesNo(unprocessedReportedSupported), effects, SAMPLE_RATE, bufferSize, rms, tone, floor, peak);
        return lastRxDebug;
    }

    private RecordStart createAudioRecord(int preferredSource, int bufferSize) {
        int[] candidates = new int[]{preferredSource, MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        String errors = "";
        for (int i = 0; i < candidates.length; i++) {
            int source = candidates[i];
            if (source == SOURCE_AUTO) continue;
            if (source == MediaRecorder.AudioSource.UNPROCESSED && !unprocessedReportedSupported && preferredSource != source) continue;
            boolean duplicate = false;
            for (int j = 0; j < i; j++) if (candidates[j] == source) duplicate = true;
            if (duplicate) continue;
            try {
                AudioRecord record = new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                if (record.getState() == AudioRecord.STATE_INITIALIZED) return new RecordStart(record, source);
                record.release();
            } catch (Exception ex) {
                errors = errors + audioSourceName(source) + " ";
            }
        }
        throw new IllegalStateException("No AudioRecord source initialized. " + errors);
    }

    private String attachAndDisableEffects(int sessionId) {
        return "AGC " + disableAgc(sessionId) + " / NS " + disableNs(sessionId) + " / AEC " + disableAec(sessionId);
    }

    private String disableAgc(int sessionId) {
        if (!AutomaticGainControl.isAvailable()) return "unavailable";
        AutomaticGainControl effect = null;
        try {
            effect = AutomaticGainControl.create(sessionId);
            if (effect == null) return "failed";
            effect.setEnabled(false);
            return effect.getEnabled() ? "failed" : "disabled";
        } catch (Exception ex) {
            return "failed";
        } finally {
            if (effect != null) effect.release();
        }
    }

    private String disableNs(int sessionId) {
        if (!NoiseSuppressor.isAvailable()) return "unavailable";
        NoiseSuppressor effect = null;
        try {
            effect = NoiseSuppressor.create(sessionId);
            if (effect == null) return "failed";
            effect.setEnabled(false);
            return effect.getEnabled() ? "failed" : "disabled";
        } catch (Exception ex) {
            return "failed";
        } finally {
            if (effect != null) effect.release();
        }
    }

    private String disableAec(int sessionId) {
        if (!AcousticEchoCanceler.isAvailable()) return "unavailable";
        AcousticEchoCanceler effect = null;
        try {
            effect = AcousticEchoCanceler.create(sessionId);
            if (effect == null) return "failed";
            effect.setEnabled(false);
            return effect.getEnabled() ? "failed" : "disabled";
        } catch (Exception ex) {
            return "failed";
        } finally {
            if (effect != null) effect.release();
        }
    }

    private ToneMetrics detectTone(short[] samples, int count, double toneHz) {
        double omega = 2.0 * Math.PI * toneHz / SAMPLE_RATE;
        double coeff = 2.0 * Math.cos(omega);
        double q0;
        double q1 = 0.0;
        double q2 = 0.0;
        double total = 0.0;
        for (int i = 0; i < count; i++) {
            double s = samples[i] / 32768.0;
            q0 = coeff * q1 - q2 + s;
            q2 = q1;
            q1 = q0;
            total += s * s;
        }
        double tonePower = q1 * q1 + q2 * q2 - coeff * q1 * q2;
        double normalized = total <= 0.000001 ? 0.0 : tonePower / (total * count);
        return new ToneMetrics(Math.max(0.0, Math.min(1.0, normalized)), Math.sqrt(total / Math.max(1, count)));
    }

    private void stopPlayback() {
        playing = false;
        releaseTrack();
        if (playbackThread != null) playbackThread.interrupt();
        if (playButton != null) playButton.setText("Play speaker");
    }

    private void stopListening() {
        listening = false;
        releaseRecord();
        if (receiveThread != null) receiveThread.interrupt();
        if (listenButton != null) listenButton.setText("Listen mic");
    }

    private void releaseTrack() {
        AudioTrack track = audioTrack;
        audioTrack = null;
        if (track != null) {
            try { track.stop(); } catch (IllegalStateException ignored) { }
            track.release();
        }
    }

    private void releaseRecord() {
        AudioRecord record = audioRecord;
        audioRecord = null;
        if (record != null) {
            try { record.stop(); } catch (IllegalStateException ignored) { }
            record.release();
        }
    }

    private boolean isUnprocessedReportedSupported() {
        try {
            AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
            String value = manager == null ? null : manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
            return "true".equalsIgnoreCase(value);
        } catch (Exception ignored) {
            return false;
        }
    }

    private int chooseDefaultAudioSource() {
        if (unprocessedReportedSupported) return MediaRecorder.AudioSource.UNPROCESSED;
        return MediaRecorder.AudioSource.VOICE_RECOGNITION;
    }

    private String audioSourceName(int source) {
        if (source == MediaRecorder.AudioSource.UNPROCESSED) return "UNPROCESSED";
        if (source == MediaRecorder.AudioSource.VOICE_RECOGNITION) return "VOICE_RECOGNITION";
        if (source == MediaRecorder.AudioSource.MIC) return "MIC";
        if (Build.VERSION.SDK_INT >= 29 && source == MediaRecorder.AudioSource.VOICE_PERFORMANCE) return "VOICE_PERFORMANCE";
        return "AUTO";
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private ArrayList<boolean[]> testColumns(int testMode, double dotRate) {
        if (testMode == TEST_CARRIER) {
            ArrayList<boolean[]> one = new ArrayList<boolean[]>();
            boolean[] full = new boolean[ROWS];
            for (int row = 0; row < ROWS; row++) full[row] = true;
            one.add(full);
            return one;
        }
        if (testMode == TEST_REPEAT) return textToColumns("HELLSCHREIBER HELLSCHREIBER HELLSCHREIBER");
        if (testMode == TEST_CQ) return textToColumns("CQ CQ HELLSCHREIBER 123");
        ArrayList<boolean[]> columns = new ArrayList<boolean[]>();
        int count = testMode == TEST_BARS ? 64 : 96;
        for (int i = 0; i < count; i++) {
            boolean[] column = new boolean[ROWS];
            boolean on = testMode == TEST_BARS ? (i % 6 == 0 || i % 6 == 1) : (i % 4 < 2);
            for (int row = 0; row < ROWS; row++) column[row] = on;
            columns.add(column);
        }
        return txPreamble ? addPreamble(columns, dotRate) : columns;
    }

    private ArrayList<boolean[]> addPreamble(ArrayList<boolean[]> payload, double dotRate) {
        ArrayList<boolean[]> out = new ArrayList<boolean[]>();
        int carrierColumns = Math.max(1, (int) Math.round(0.5 * dotRate / ROWS));
        for (int i = 0; i < carrierColumns; i++) {
            boolean[] full = new boolean[ROWS];
            for (int row = 0; row < ROWS; row++) full[row] = true;
            out.add(full);
        }
        for (int i = 0; i < 14; i++) {
            boolean[] column = new boolean[ROWS];
            boolean on = i % 2 == 0;
            for (int row = 0; row < ROWS; row++) column[row] = on;
            out.add(column);
        }
        out.addAll(payload);
        return out;
    }

    private void startCalibration() {
        final double toneHz = Math.max(50.0, readDouble(toneInput, 1000.0));
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCalibration = true;
            pendingCalibrationTone = toneHz;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST);
            return;
        }
        stopPlayback();
        stopListening();
        calibrateButton.setText("Calibrating...");
        new CalibrationThread(this, toneHz).start();
    }

    private void calibrateNoise(double toneHz) {
        int dotSamples = Math.max(8, (int) Math.round(SAMPLE_RATE / Math.max(1.0, readDouble(dotRateInput, 122.5))));
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, dotSamples * 8);
        AudioRecord record = null;
        double sumEnergy = 0.0;
        double sumRms = 0.0;
        int dots = 0;
        try {
            RecordStart start = createAudioRecord(requestedAudioSource, bufferSize);
            record = start.record;
            attachAndDisableEffects(record.getAudioSessionId());
            short[] dot = new short[dotSamples];
            short[] buffer = new short[bufferSize / 2];
            int fill = 0;
            long end = System.currentTimeMillis() + 1000L;
            record.startRecording();
            while (System.currentTimeMillis() < end) {
                int read = record.read(buffer, 0, buffer.length);
                for (int i = 0; i < read; i++) {
                    dot[fill++] = buffer[i];
                    if (fill == dotSamples) {
                        ToneMetrics metrics = detectTone(dot, dotSamples, toneHz);
                        sumEnergy += metrics.tone;
                        sumRms += metrics.rms;
                        dots++;
                        fill = 0;
                    }
                }
            }
            calibratedNoiseFloor = dots == 0 ? 0.0 : sumEnergy / dots;
            final double suggested = Math.max(0.05, Math.min(0.95, calibratedNoiseFloor * 3.0 + 0.08));
            runOnUiThread(new CalibrationFinishedUpdate(this, String.format(Locale.US,
                    "Calibrated floor %.3f, RMS %.3f. Suggested threshold %.2f.",
                    calibratedNoiseFloor, dots == 0 ? 0.0 : sumRms / dots, suggested), suggested));
        } catch (Exception ex) {
            runOnUiThread(new CalibrationFinishedUpdate(this, "Calibration failed; try a different RX source.", -1.0));
        } finally {
            if (record != null) {
                try { record.stop(); } catch (IllegalStateException ignored) { }
                record.release();
            }
        }
    }

    private ArrayList<boolean[]> textToColumns(String text) {
        ArrayList<boolean[]> columns = new ArrayList<boolean[]>();
        String upper = text.toUpperCase(Locale.US);
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            String[] glyph = FONT.get(Character.valueOf(ch));
            if (glyph == null) glyph = FONT.get(Character.valueOf(' '));
            for (int x = 0; x < 5; x++) {
                boolean[] column = new boolean[ROWS];
                for (int y = 0; y < ROWS; y++) column[y] = glyph[y].charAt(x) == '#';
                columns.add(column);
            }
            int spacing = ch == ' ' ? WORD_SPACING : CHAR_SPACING;
            for (int s = 0; s < spacing; s++) columns.add(new boolean[ROWS]);
        }
        if (columns.isEmpty()) columns.add(new boolean[ROWS]);
        return columns;
    }

    private String asciiFromColumns(ArrayList<boolean[]> columns) {
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            for (int c = 0; c < columns.size(); c++) out.append(columns.get(c)[row] ? '#' : ' ');
            if (row < ROWS - 1) out.append('\n');
        }
        return out.toString();
    }

    private LinearLayout panel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(12), dp(12), dp(12), dp(12));
        layout.setBackgroundColor(panel);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        layout.setLayoutParams(params);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0, dp(8), 0, 0);
        return layout;
    }

    private EditText control(LinearLayout parent, String label, String value) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, 0, dp(8), 0);
        group.addView(label(label));
        EditText field = new EditText(this);
        field.setText(value);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        styleField(field);
        group.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        parent.addView(group, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return field;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, muted);
        label.setPadding(0, 0, 0, dp(4));
        return label;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(accentText);
        button.setBackgroundColor(accent);
        button.setMinHeight(dp(54));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(ink);
        button.setBackgroundColor(line);
        button.setMinHeight(dp(54));
        return button;
    }

    private void styleField(TextView view) {
        view.setTextColor(ink);
        view.setHintTextColor(muted);
        view.setBackgroundColor(field);
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, dp(6), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int readInt(EditText field, int fallback) {
        try { return Math.max(1, Integer.parseInt(field.getText().toString().trim())); }
        catch (Exception ignored) { return fallback; }
    }

    private double readDouble(EditText field, double fallback) {
        try { return Double.parseDouble(field.getText().toString().trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private void setStatus(String value) {
        runOnUiThread(new StatusUpdate(this, value));
    }

    private static final class PlaybackThread extends Thread {
        private final MainActivity activity;
        private final ArrayList<boolean[]> columns;
        private final double toneHz;
        private final double dotRate;
        private final boolean solidCarrier;

        PlaybackThread(MainActivity activity, ArrayList<boolean[]> columns, double toneHz, double dotRate,
                       boolean solidCarrier) {
            super("hell-playback");
            this.activity = activity;
            this.columns = columns;
            this.toneHz = toneHz;
            this.dotRate = dotRate;
            this.solidCarrier = solidCarrier;
        }

        @Override
        public void run() {
            activity.playColumns(columns, toneHz, dotRate, solidCarrier);
        }
    }

    private static final class CalibrationThread extends Thread {
        private final MainActivity activity;
        private final double toneHz;

        CalibrationThread(MainActivity activity, double toneHz) {
            super("hell-calibrate");
            this.activity = activity;
            this.toneHz = toneHz;
        }

        @Override
        public void run() {
            activity.calibrateNoise(toneHz);
        }
    }

    private static final class ReceiveThread extends Thread {
        private final MainActivity activity;
        private final double toneHz;
        private final double dotRate;
        private final double contrast;

        ReceiveThread(MainActivity activity, double toneHz, double dotRate, double contrast) {
            super("hell-receive");
            this.activity = activity;
            this.toneHz = toneHz;
            this.dotRate = dotRate;
            this.contrast = contrast;
        }

        @Override
        public void run() {
            activity.receiveMicrophone(toneHz, dotRate, contrast);
        }
    }

    private static final class RecordStart {
        final AudioRecord record;
        final int source;

        RecordStart(AudioRecord record, int source) {
            this.record = record;
            this.source = source;
        }
    }

    private static final class ToneMetrics {
        final double tone;
        final double rms;

        ToneMetrics(double tone, double rms) {
            this.tone = tone;
            this.rms = rms;
        }
    }

    private static final class PlaybackFinishedUpdate implements Runnable {
        private final MainActivity activity;
        private final String message;

        PlaybackFinishedUpdate(MainActivity activity, String message) {
            this.activity = activity;
            this.message = message;
        }

        public void run() {
            activity.playing = false;
            activity.playButton.setText("Play speaker");
            activity.status.setText(message);
        }
    }

    private static final class CalibrationFinishedUpdate implements Runnable {
        private final MainActivity activity;
        private final String message;
        private final double threshold;

        CalibrationFinishedUpdate(MainActivity activity, String message, double threshold) {
            this.activity = activity;
            this.message = message;
            this.threshold = threshold;
        }

        public void run() {
            activity.calibrateButton.setText("Calibrate noise");
            if (threshold >= 0.0) activity.thresholdInput.setText(String.format(Locale.US, "%.2f", threshold));
            activity.status.setText(message);
        }
    }

    private static final class ReceiveFinishedUpdate implements Runnable {
        private final MainActivity activity;

        ReceiveFinishedUpdate(MainActivity activity) {
            this.activity = activity;
        }

        public void run() {
            activity.listening = false;
            activity.listenButton.setText("Listen mic");
        }
    }

    private static final class StatusUpdate implements Runnable {
        private final MainActivity activity;
        private final String value;

        StatusUpdate(MainActivity activity, String value) {
            this.activity = activity;
            this.value = value;
        }

        public void run() {
            activity.status.setText(value);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static Map<Character, String[]> buildFont() {
        Map<Character, String[]> font = new HashMap<Character, String[]>();
        put(font, ' ', "     ", "     ", "     ", "     ", "     ", "     ", "     ");
        put(font, '!', "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "     ", "  #  ");
        put(font, '?', " ### ", "#   #", "    #", "   # ", "  #  ", "     ", "  #  ");
        put(font, '.', "     ", "     ", "     ", "     ", "     ", "     ", "  #  ");
        put(font, ',', "     ", "     ", "     ", "     ", "     ", "  #  ", " #   ");
        put(font, '-', "     ", "     ", "     ", " ### ", "     ", "     ", "     ");
        put(font, '/', "    #", "    #", "   # ", "  #  ", " #   ", "#    ", "#    ");
        put(font, ':', "     ", "  #  ", "     ", "     ", "     ", "  #  ", "     ");
        put(font, '0', " ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### ");
        put(font, '1', "  #  ", " ##  ", "# #  ", "  #  ", "  #  ", "  #  ", "#####");
        put(font, '2', " ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####");
        put(font, '3', " ### ", "#   #", "    #", "  ## ", "    #", "#   #", " ### ");
        put(font, '4', "   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # ");
        put(font, '5', "#####", "#    ", "#    ", "#### ", "    #", "#   #", " ### ");
        put(font, '6', " ### ", "#   #", "#    ", "#### ", "#   #", "#   #", " ### ");
        put(font, '7', "#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   ");
        put(font, '8', " ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### ");
        put(font, '9', " ### ", "#   #", "#   #", " ####", "    #", "#   #", " ### ");
        put(font, 'A', " ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #");
        put(font, 'B', "#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### ");
        put(font, 'C', " ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### ");
        put(font, 'D', "#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### ");
        put(font, 'E', "#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####");
        put(font, 'F', "#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    ");
        put(font, 'G', " ### ", "#   #", "#    ", "# ###", "#   #", "#   #", " ### ");
        put(font, 'H', "#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #");
        put(font, 'I', "#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "#####");
        put(font, 'J', "#####", "    #", "    #", "    #", "    #", "#   #", " ### ");
        put(font, 'K', "#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #");
        put(font, 'L', "#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####");
        put(font, 'M', "#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #");
        put(font, 'N', "#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #");
        put(font, 'O', " ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### ");
        put(font, 'P', "#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    ");
        put(font, 'Q', " ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #");
        put(font, 'R', "#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #");
        put(font, 'S', " ####", "#    ", "#    ", " ### ", "    #", "    #", "#### ");
        put(font, 'T', "#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ");
        put(font, 'U', "#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### ");
        put(font, 'V', "#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  ");
        put(font, 'W', "#   #", "#   #", "#   #", "# # #", "# # #", "## ##", "#   #");
        put(font, 'X', "#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #");
        put(font, 'Y', "#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  ");
        put(font, 'Z', "#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####");
        return font;
    }

    private static void put(Map<Character, String[]> font, char key, String... rows) {
        font.put(Character.valueOf(key), rows);
    }

    private static class HellStripView extends View {
        private final Paint paint = new Paint();
        private final ArrayList<float[]> energyColumns = new ArrayList<float[]>();
        private int backgroundColor;
        private int inkColor;
        private int accentColor;
        private int gridColor;
        private int scale = 8;
        private double threshold = 0.45;
        private int wrapOffset;
        private float lastTouchY;

        HellStripView(Activity context) {
            super(context);
            paint.setAntiAlias(false);
        }

        void setPalette(int backgroundColor, int inkColor, int accentColor, int gridColor) {
            this.backgroundColor = backgroundColor;
            this.inkColor = inkColor;
            this.accentColor = accentColor;
            this.gridColor = gridColor;
        }

        void setScale(int scale) {
            this.scale = Math.max(2, Math.min(32, scale));
            requestLayout();
            invalidate();
        }

        void setThreshold(double threshold) {
            this.threshold = Math.max(0.0, Math.min(1.0, threshold));
            invalidate();
        }

        void setColumns(ArrayList<boolean[]> columns) {
            synchronized (energyColumns) {
                energyColumns.clear();
                for (int c = 0; c < columns.size(); c++) {
                    boolean[] column = columns.get(c);
                    float[] energy = new float[ROWS];
                    for (int row = 0; row < ROWS; row++) energy[row] = column[row] ? 1.0f : 0.0f;
                    energyColumns.add(energy);
                }
            }
            requestLayout();
            invalidate();
        }

        void clearEnergy() {
            synchronized (energyColumns) {
                energyColumns.clear();
            }
            requestLayout();
            invalidate();
        }

        void appendEnergy(float[] column) {
            post(new AppendEnergyUpdate(this, column));
        }

        private static final class AppendEnergyUpdate implements Runnable {
            private final HellStripView stripView;
            private final float[] column;

            AppendEnergyUpdate(HellStripView stripView, float[] column) {
                this.stripView = stripView;
                this.column = column;
            }

            public void run() {
                synchronized (stripView.energyColumns) {
                    stripView.energyColumns.add(column);
                    if (stripView.energyColumns.size() > 1200) stripView.energyColumns.remove(0);
                }
                stripView.requestLayout();
                stripView.invalidate();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int count;
            synchronized (energyColumns) {
                count = Math.max(1, energyColumns.size());
            }
            int desiredWidth = count * scale + getPaddingLeft() + getPaddingRight();
            int desiredHeight = ROWS * scale + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(backgroundColor);
            paint.setStyle(Paint.Style.FILL);
            ArrayList<float[]> snapshot;
            synchronized (energyColumns) {
                snapshot = new ArrayList<float[]>(energyColumns);
            }
            int stripHeight = ROWS * scale;
            for (int col = 0; col < snapshot.size(); col++) {
                float[] column = snapshot.get(col);
                for (int row = 0; row < ROWS; row++) {
                    float value = Math.max(0f, Math.min(1f, column[row]));
                    if (value <= 0f) continue;
                    paint.setColor(value >= threshold ? inkColor : blend(backgroundColor, accentColor, value));
                    int y = positiveModulo(row * scale + wrapOffset, stripHeight);
                    drawWrappedCell(canvas, col * scale, y, (col + 1) * scale, y + scale, stripHeight, paint);
                }
            }
            paint.setColor(gridColor);
            for (int row = 1; row < ROWS; row++) {
                float y = positiveModulo(row * scale + wrapOffset, stripHeight);
                canvas.drawLine(0, y, getWidth(), y, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchY = event.getY();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                int stripHeight = Math.max(1, ROWS * scale);
                wrapOffset = positiveModulo(wrapOffset + Math.round(event.getY() - lastTouchY), stripHeight);
                lastTouchY = event.getY();
                invalidate();
                return true;
            }
            return true;
        }

        private void drawWrappedCell(Canvas canvas, int left, int top, int right, int bottom, int stripHeight, Paint paint) {
            if (bottom <= stripHeight) {
                canvas.drawRect(left, top, right, bottom, paint);
            } else {
                canvas.drawRect(left, top, right, stripHeight, paint);
                canvas.drawRect(left, 0, right, bottom - stripHeight, paint);
            }
        }

        private int positiveModulo(int value, int mod) {
            int result = value % mod;
            return result < 0 ? result + mod : result;
        }

        private int blend(int from, int to, float amount) {
            float t = Math.max(0f, Math.min(1f, amount));
            int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
            int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
            int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
            int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
            return Color.argb(a, r, g, b);
        }
    }
}
