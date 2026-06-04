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
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
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

    private volatile boolean playing;
    private volatile boolean listening;
    private Thread playbackThread;
    private Thread receiveThread;
    private AudioTrack audioTrack;
    private AudioRecord audioRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configurePalette();
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
        controls.addView(row1);
        controls.addView(row2);
        toneInput = control(row1, "Tone Hz", "1000");
        dotRateInput = control(row1, "Dot rate", "122.5");
        scaleInput = control(row1, "Scale", "9");
        thresholdInput = control(row2, "RX threshold", "0.45");
        contrastInput = control(row2, "RX contrast", "3.0");

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
        }
    }

    private void renderMessage() {
        ArrayList<boolean[]> columns = textToColumns(messageInput.getText().toString());
        stripView.setScale(readInt(scaleInput, 9));
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
        final ArrayList<boolean[]> columns = textToColumns(messageInput.getText().toString());
        final double toneHz = Math.max(50.0, readDouble(toneInput, 1000.0));
        final double dotRate = Math.max(1.0, readDouble(dotRateInput, 122.5));
        playing = true;
        playButton.setText("Stop play");
        playbackThread = new PlaybackThread(this, columns, toneHz, dotRate);
        playbackThread.start();
    }

    private void playColumns(ArrayList<boolean[]> columns, double toneHz, double dotRate) {
        int dotSamples = Math.max(8, (int) Math.round(SAMPLE_RATE / dotRate));
        int totalSamples = Math.max(1, columns.size() * ROWS * dotSamples);
        short[] pcm = new short[totalSamples];
        double phase = 0.0;
        double step = 2.0 * Math.PI * toneHz / SAMPLE_RATE;
        int index = 0;
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
        String finalStatus = "Playback finished.";
        try {
            int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer, pcm.length * 2), AudioTrack.MODE_STREAM);
            audioTrack.play();
            setStatus("Playing Hell tone over the speaker.");
            int written = 0;
            while (playing && written < pcm.length) {
                int result = audioTrack.write(pcm, written, Math.min(1024, pcm.length - written));
                if (result <= 0) break;
                written += result;
            }
        } catch (IllegalStateException ex) {
            finalStatus = "Speaker playback could not start on this device.";
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
            startListening();
        } else {
            setStatus("Microphone permission is needed for receive.");
        }
    }

    private void startListening() {
        stopPlayback();
        stripView.setScale(readInt(scaleInput, 9));
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
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioRecord.startRecording();
            setStatus("Listening through the microphone. Hold a Hell tone near the phone.");
            while (listening) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                for (int i = 0; i < read && listening; i++) {
                    dot[dotFill++] = buffer[i];
                    if (dotFill == dotSamples) {
                        column[row] = detectEnergy(dot, dotSamples, toneHz, contrast);
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
            setStatus("Microphone receive could not start on this device.");
        } finally {
            releaseRecord();
            runOnUiThread(new ReceiveFinishedUpdate(this));
        }
    }

    private float detectEnergy(short[] samples, int count, double toneHz, double contrast) {
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
        return (float) Math.max(0.0, Math.min(1.0, normalized * contrast));
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

        PlaybackThread(MainActivity activity, ArrayList<boolean[]> columns, double toneHz, double dotRate) {
            super("hell-playback");
            this.activity = activity;
            this.columns = columns;
            this.toneHz = toneHz;
            this.dotRate = dotRate;
        }

        @Override
        public void run() {
            activity.playColumns(columns, toneHz, dotRate);
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
        private int scale = 9;
        private double threshold = 0.45;

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
            for (int col = 0; col < snapshot.size(); col++) {
                float[] column = snapshot.get(col);
                for (int row = 0; row < ROWS; row++) {
                    float value = Math.max(0f, Math.min(1f, column[row]));
                    if (value <= 0f) continue;
                    paint.setColor(value >= threshold ? inkColor : blend(backgroundColor, accentColor, value));
                    canvas.drawRect(col * scale, row * scale, (col + 1) * scale, (row + 1) * scale, paint);
                }
            }
            paint.setColor(gridColor);
            for (int row = 1; row < ROWS; row++) {
                float y = row * scale;
                canvas.drawLine(0, y, getWidth(), y, paint);
            }
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
