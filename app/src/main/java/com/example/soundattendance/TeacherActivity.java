package com.example.soundattendance;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TeacherActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 44100;

    private TextView tvStatus;
    private Button btnStart, btnEnd;
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> presentList = new ArrayList<>();

    private AudioTrack track;
    private DatabaseReference root;
    private DatabaseReference presentRef;
    private ValueEventListener presentListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);

        tvStatus = findViewById(R.id.tvStatus);
        btnStart = findViewById(R.id.btnStartClass);
        btnEnd = findViewById(R.id.btnEndClass);
        ListView listView = findViewById(R.id.listPresent);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, presentList);
        listView.setAdapter(adapter);

        root = FirebaseDatabase.getInstance().getReference();
        btnEnd.setEnabled(false);

        btnStart.setOnClickListener(v -> startClass());
        btnEnd.setOnClickListener(v -> endClass());
    }

    private void startClass() {
        // random frequency between 2000 and 3500 Hz, in steps of 100 Hz
        int freq = 2000 + new Random().nextInt(16) * 100;
        String sessionId = String.valueOf(System.currentTimeMillis());

        Map<String, Object> session = new HashMap<>();
        session.put("freq", freq);
        session.put("id", sessionId);
        session.put("active", true);
        session.put("startedAt", System.currentTimeMillis());
        root.child("active_session").setValue(session);

        // live list of present students
        presentList.clear();
        adapter.notifyDataSetChanged();
        presentRef = root.child("sessions").child(sessionId).child("present");
        presentListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                presentList.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    presentList.add(s.getKey() + "  -  " + s.getValue(String.class));
                }
                adapter.notifyDataSetChanged();
                tvStatus.setText("Class running (" + freq + " Hz)\nPresent: " + presentList.size());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        presentRef.addValueEventListener(presentListener);

        playTone(freq);
        tvStatus.setText("Class running (" + freq + " Hz)\nPresent: 0\nKeep volume high.");
        btnStart.setEnabled(false);
        btnEnd.setEnabled(true);
    }

    private void endClass() {
        stopTone();
        root.child("active_session").child("active").setValue(false);
        if (presentRef != null && presentListener != null) {
            presentRef.removeEventListener(presentListener);
        }
        tvStatus.setText("Class ended. Total present: " + presentList.size());
        btnStart.setEnabled(true);
        btnEnd.setEnabled(false);
    }

    // Plays a looping pure tone. 1 second of audio holds exactly "freq" cycles
    // (for whole-number Hz), so the loop is seamless.
    private void playTone(int freq) {
        stopTone();
        int n = SAMPLE_RATE;
        short[] buf = new short[n];
        for (int i = 0; i < n; i++) {
            buf[i] = (short) (Math.sin(2 * Math.PI * i * freq / SAMPLE_RATE) * 30000);
        }
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        track.write(buf, 0, n);
        track.setLoopPoints(0, n, -1); // loop forever
        track.play();
    }

    private void stopTone() {
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) { }
            track.release();
            track = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTone();
        if (presentRef != null && presentListener != null) {
            presentRef.removeEventListener(presentListener);
        }
    }
}
