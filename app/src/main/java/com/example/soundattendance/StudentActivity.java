package com.example.soundattendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

public class StudentActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 44100;
    private static final int REQ_AUDIO = 101;
    // tone must be this many times stronger than the surrounding frequencies
    private static final double RATIO_NEEDED = 8.0;

    private EditText etName, etRoll;
    private Button btnMark;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        etName = findViewById(R.id.etName);
        etRoll = findViewById(R.id.etRoll);
        btnMark = findViewById(R.id.btnMark);
        tvResult = findViewById(R.id.tvResult);

        btnMark.setOnClickListener(v -> {
            if (etName.getText().toString().trim().isEmpty()
                    || etRoll.getText().toString().trim().isEmpty()) {
                tvResult.setText("Enter your name and roll number first.");
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            } else {
                markAttendance();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            markAttendance();
        } else if (code == REQ_AUDIO) {
            tvResult.setText("Microphone permission is needed to mark attendance.");
        }
    }

    private void markAttendance() {
        btnMark.setEnabled(false);
        tvResult.setText("Checking for active class...");

        FirebaseDatabase.getInstance().getReference("active_session").get()
                .addOnSuccessListener((DataSnapshot snap) -> {
                    Boolean active = snap.child("active").getValue(Boolean.class);
                    Integer freq = snap.child("freq").getValue(Integer.class);
                    String sessionId = snap.child("id").getValue(String.class);

                    if (active == null || !active || freq == null || sessionId == null) {
                        tvResult.setText("No class is running right now.");
                        btnMark.setEnabled(true);
                        return;
                    }
                    tvResult.setText("Listening... keep your phone near the teacher's phone.");
                    listenAndMark(freq, sessionId);
                })
                .addOnFailureListener(e -> {
                    tvResult.setText("Network error: " + e.getMessage());
                    btnMark.setEnabled(true);
                });
    }

    private void listenAndMark(int freq, String sessionId) {
        final String name = etName.getText().toString().trim();
        final String roll = etRoll.getText().toString().trim().replace(".", "_")
                .replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_").replace("/", "_");

        new Thread(() -> {
            boolean heard = false;
            try {
                heard = detectTone(freq);
            } catch (SecurityException | IllegalStateException ex) {
                // permission or recorder problem -> heard stays false
            }
            final boolean ok = heard;
            runOnUiThread(() -> {
                if (ok) {
                    FirebaseDatabase.getInstance().getReference("sessions")
                            .child(sessionId).child("present").child(roll).setValue(name)
                            .addOnSuccessListener(a -> {
                                tvResult.setText("PRESENT - attendance marked for " + name);
                                btnMark.setEnabled(true);
                            })
                            .addOnFailureListener(e -> {
                                tvResult.setText("Heard the tone, but saving failed: " + e.getMessage());
                                btnMark.setEnabled(true);
                            });
                } else {
                    tvResult.setText("Tone not detected. Move closer, raise teacher volume, and try again.");
                    btnMark.setEnabled(true);
                }
            });
        }).start();
    }

    // Records ~1.5 s and checks the 3 half-second windows. Returns true if any window has the tone.
    private boolean detectTone(int freq) {
        int window = SAMPLE_RATE / 2;
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, window * 2));
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            return false;
        }

        boolean found = false;
        short[] samples = new short[window];
        rec.startRecording();
        try {
            for (int w = 0; w < 3 && !found; w++) {
                int read = 0;
                while (read < window) {
                    int r = rec.read(samples, read, window - read);
                    if (r <= 0) return false;
                    read += r;
                }
                found = toneIsPresent(samples, freq);
            }
        } finally {
            rec.stop();
            rec.release();
        }
        return found;
    }

    // Compare power at the target frequency with the average power at nearby frequencies.
    private boolean toneIsPresent(short[] s, int freq) {
        double target = goertzel(s, freq);
        double noise = (goertzel(s, freq - 500) + goertzel(s, freq + 500)
                + goertzel(s, freq - 1000) + goertzel(s, freq + 1000)) / 4.0;
        if (noise < 1) noise = 1;
        return target / noise > RATIO_NEEDED;
    }

    // Goertzel: strength of ONE frequency in the samples (simpler than a full FFT).
    private double goertzel(short[] samples, int freq) {
        double coeff = 2 * Math.cos(2 * Math.PI * freq / SAMPLE_RATE);
        double s1 = 0, s2 = 0;
        for (short x : samples) {
            double s0 = x + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2;
    }
}
